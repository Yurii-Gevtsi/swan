package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EventEntity
import com.example.ui.viewmodel.OsintViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: OsintViewModel,
    onNavigateToEventDetails: () -> Unit
) {
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"

    val events by viewModel.filteredEvents.collectAsState()

    // Filters (page 11)
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedTheater by viewModel.selectedTheater.collectAsState()
    val selectedScope by viewModel.selectedScope.collectAsState()
    val selectedVerificationStatus by viewModel.selectedVerificationStatus.collectAsState()

    var showFiltersRow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isUk) "ХРОНОЛОГІЧНИЙ РЕЄСТР" else "DOCUMENTED TIMELINE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = { showFiltersRow = !showFiltersRow }) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = "Filter Toggle",
                            tint = if (selectedCategory != null || selectedTheater != null || selectedScope != null || selectedVerificationStatus != null) Color(0xFF38BDF8) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF090D16))
            )
        },
        containerColor = Color(0xFF090D16)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Animated Filters Row (page 11)
            AnimatedVisibility(visible = showFiltersRow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF1E293B))
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (isUk) "Швидкі фільтри" else "Quick Filters",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row of quick resets or selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUk) "Категорія: ${selectedCategory ?: "Всі"}" else "Category: ${selectedCategory ?: "All"}",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        if (selectedCategory != null || selectedTheater != null || selectedScope != null || selectedVerificationStatus != null) {
                            Text(
                                text = if (isUk) "Скинути фільтри" else "Reset All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier
                                    .clickable { viewModel.resetFilters() }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Theater filters
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val theaters = listOf("RUSSIA_INTERNAL", "BLACK_SEA", "BALTIC_SEA")
                        theaters.forEach { th ->
                            val active = selectedTheater == th
                            Box(
                                modifier = Modifier
                                    .background(if (active) Color.White else Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                    .clickable { viewModel.selectedTheater.value = if (active) null else th }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = th.replace("_", " "),
                                    color = if (active) Color(0xFF020617) else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            if (events.isEmpty()) {
                // Empty state (page 39 design guidelines)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "No events",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isUk) "Не знайдено жодного запису" else "No matching OSINT records found",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(events) { event ->
                        TimelineCard(
                            event = event,
                            isUk = isUk,
                            onClick = {
                                viewModel.selectEvent(event)
                                onNavigateToEventDetails()
                            }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun TimelineCard(
    event: EventEntity,
    isUk: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Neutral documentary presentation tag (page 11: "Use: Documented update / Updated record / Corrected record")
            val recordTag = when (event.verificationStatus) {
                "OFFICIAL_CONFIRMED", "INSTITUTIONAL_CONFIRMED" -> if (isUk) "Задокументоване оновлення" else "Documented update"
                "UPDATED" -> if (isUk) "Оновлений запис" else "Updated record"
                "CORRECTED" -> if (isUk) "Виправлений запис" else "Corrected record"
                else -> if (isUk) "Новий історичний запис" else "New historical record"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = recordTag.uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = event.date,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = if (isUk) event.titleUk else event.titleEn,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Summary text (neutral, non-sensationalist)
            Text(
                text = if (isUk) event.summaryUk else event.summaryEn,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = Color(0xFF1E293B))

            Spacer(modifier = Modifier.height(10.dp))

            // Details footer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Location",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isUk) event.approximateLocationLabelUk else event.approximateLocationLabelEn,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = "Sources count",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${event.sources.split(",").size} ${if (isUk) "джерела" else "sources"}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
