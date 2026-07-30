package com.gysignalstudio.blackswan.ui.screens

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
import com.gysignalstudio.blackswan.data.model.EventEntity
import com.gysignalstudio.blackswan.ui.viewmodel.OsintViewModel

private val brokenTimelineEncodingMarkers = listOf(
    "\u0420\u00A0",
    "\u0420\u2019",
    "\u0420\u040B",
    "\u0420\u045F",
    "\u0421\u2013",
    "\u0432\u0402",
    "Рџ",
    "Рњ",
    "Р†",
    "РЎ",
    "СЃ",
    "С–",
    "вЂ",
)

private fun String.hasBrokenTimelineEncoding(): Boolean =
    brokenTimelineEncodingMarkers.any(::contains)

private fun localizedTimelineText(isUk: Boolean, uk: String, en: String): String =
    if (isUk && uk.isNotBlank() && !uk.hasBrokenTimelineEncoding()) uk else en

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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = { showFiltersRow = !showFiltersRow }) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = "Filter Toggle",
                            tint = if (selectedCategory != null || selectedTheater != null || selectedScope != null || selectedVerificationStatus != null) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (isUk) "Швидкі фільтри" else "Quick Filters",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurface
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
                                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .clickable { viewModel.selectedTheater.value = if (active) null else th }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = th.replace("_", " "),
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(VERIFICATION_CONFIRMED, VERIFICATION_REPORTED, VERIFICATION_DISPUTED).forEach { status ->
                            val active = selectedVerificationStatus == status
                            Box(
                                modifier = Modifier
                                    .background(if (active) verificationColor(status) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .clickable { viewModel.selectedVerificationStatus.value = if (active) null else status }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = verificationLabel(status, isUk),
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else verificationColor(status),
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
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isUk) "Не знайдено жодного запису" else "No matching OSINT records found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Neutral documentary presentation tag (page 11: "Use: Documented update / Updated record / Corrected record")
            val recordTag = verificationLabel(event.verificationStatus, isUk)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = localizedTimelineText(isUk, event.titleUk, event.titleEn),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Summary text (neutral, non-sensationalist)
            Text(
                text = localizedTimelineText(isUk, event.summaryUk, event.summaryEn),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

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
                    text = localizedTimelineText(isUk, event.approximateLocationLabelUk, event.approximateLocationLabelEn),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = "Sources count",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${event.sources.split(",").size} ${if (isUk) "джерела" else "sources"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false
                )
            }
        }
    }
}
