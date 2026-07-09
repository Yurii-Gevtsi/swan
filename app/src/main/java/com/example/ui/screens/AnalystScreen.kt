package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EventEntity
import com.example.data.model.RegionEntity
import com.example.ui.viewmodel.OsintViewModel
import com.example.ui.viewmodel.AiState
import com.example.ui.viewmodel.AiChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalystScreen(
    viewModel: OsintViewModel
) {
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"

    val events by viewModel.allEvents.collectAsState()
    val regions by viewModel.allRegions.collectAsState()

    val aiHistory by viewModel.aiHistory.collectAsState()
    val aiState by viewModel.aiAnalysisResult.collectAsState()

    var userMessageText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    // Context binding selection states
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    var selectedRegionId by remember { mutableStateOf<String?>(null) }

    var expandedEventDropdown by remember { mutableStateOf(false) }
    var expandedRegionDropdown by remember { mutableStateOf(false) }

    // Scroll chat history to bottom automatically when new message arrives
    LaunchedEffect(aiHistory.size) {
        if (aiHistory.isNotEmpty()) {
            lazyListState.animateScrollToItem(aiHistory.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Hub, contentDescription = "Intelligence", tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUk) "ІНТЕЛЕКТУАЛЬНИЙ АНАЛІТИК" else "AI OSINT COGNITIVE ENGINE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearAiChat() }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Chat", tint = Color.White)
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
            // Context Binder Selection Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E293B))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Event Binder Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { expandedEventDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Book, contentDescription = "Event Context", tint = if (selectedEventId != null) Color(0xFF10B981) else Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedEventId != null) {
                                events.find { it.id == selectedEventId }?.titleEn?.take(15) + "..."
                            } else {
                                if (isUk) "Прив'язати запис" else "Bind Event Log"
                            },
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = expandedEventDropdown,
                        onDismissRequest = { expandedEventDropdown = false },
                        modifier = Modifier.background(Color(0xFF0F172A))
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "None", color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                selectedEventId = null
                                expandedEventDropdown = false
                            }
                        )
                        events.forEach { event ->
                            DropdownMenuItem(
                                text = { Text(text = if (isUk) event.titleUk else event.titleEn, color = Color.White, fontSize = 12.sp, maxLines = 1) },
                                onClick = {
                                    selectedEventId = event.id
                                    selectedRegionId = null // clear conflicting
                                    expandedEventDropdown = false
                                }
                            )
                        }
                    }
                }

                // Region Binder Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { expandedRegionDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = "Region Context", tint = if (selectedRegionId != null) Color(0xFF38BDF8) else Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedRegionId != null) {
                                regions.find { it.regionId == selectedRegionId }?.nameEn?.take(15) + "..."
                            } else {
                                if (isUk) "Прив'язати регіон" else "Bind Region"
                            },
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = expandedRegionDropdown,
                        onDismissRequest = { expandedRegionDropdown = false },
                        modifier = Modifier.background(Color(0xFF0F172A))
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "None", color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                selectedRegionId = null
                                expandedRegionDropdown = false
                            }
                        )
                        regions.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(text = if (isUk) region.nameUk else region.nameEn, color = Color.White, fontSize = 12.sp, maxLines = 1) },
                                onClick = {
                                    selectedRegionId = region.regionId
                                    selectedEventId = null // clear conflicting
                                    expandedRegionDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Message History Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (aiHistory.isEmpty()) {
                    // Empty state instruction card (AI Analyst concept)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🦢",
                                fontSize = 36.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isUk) "КОГНІТИВНИЙ АНАЛІТИЧНИЙ МОДУЛЬ" else "COGNITIVE REASONING ENGINE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isUk) {
                                    "Поставте складне запитання щодо впливу санкцій, логістики або паливної інфраструктури РФ. Цей модуль використовує ШІ-модель надвисокого мислення (gemini-3.1-pro-preview) для ретельного аналізу з дотриманням застережень щодо безпеки та географії."
                                } else {
                                    "Inquire about complex systemic impacts on Russia's military-economic system, fuel infrastructure, logistics pressure, or shadow fleet. This module utilizes the high-thinking AI model (gemini-3.1-pro-preview) with deep geopolitical parameters & safety restrictions."
                                },
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(aiHistory) { chatItem ->
                            ChatBubble(message = chatItem)
                        }

                        // Thinking/Loading Active State (High Thinking Active Indicator, page 25 Additional Metadata)
                        if (aiState is AiState.Thinking) {
                            item {
                                ThinkingIndicator(isUk = isUk)
                            }
                        }
                    }
                }
            }

            // Input Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = userMessageText,
                        onValueChange = { userMessageText = it },
                        placeholder = {
                            Text(
                                text = if (isUk) "Введіть аналітичний запит..." else "Enter analysis query...",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 120.dp)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF090D16),
                            unfocusedContainerColor = Color(0xFF090D16),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (userMessageText.trim().isNotEmpty()) {
                                var fullPrompt = userMessageText
                                selectedEventId?.let { id ->
                                    fullPrompt += "\n[Analyst Note: Please specifically evaluate event logs of ID $id]"
                                }
                                selectedRegionId?.let { rid ->
                                    fullPrompt += "\n[Analyst Note: Please specifically evaluate indicators for region $rid]"
                                }
                                viewModel.askAiAnalyst(fullPrompt)
                                userMessageText = ""
                            }
                        },
                        enabled = aiState !is AiState.Thinking && userMessageText.trim().isNotEmpty(),
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (userMessageText.trim().isNotEmpty()) Color.White else Color(0xFF1E293B),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (userMessageText.trim().isNotEmpty()) Color(0xFF020617) else Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: AiChatMessage) {
    val isUser = message.role == "user"

    val bubbleBg = if (isUser) Color(0xFF1E293B) else Color(0xFF0F172A)
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleBorderColor = if (isUser) Color(0xFF334155) else Color(0xFF10B981)

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .align(alignment)
                .fillMaxWidth(0.85f)
        ) {
            // Sender role text label
            Text(
                text = if (isUser) "OPERATOR INQUIRY" else "COGNITIVE COG-ENGINE RESPONSE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isUser) Color(0xFF64748B) else Color(0xFF10B981),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .align(if (isUser) Alignment.End else Alignment.Start)
            )

            // Content Bubble
            Card(
                colors = CardDefaults.cardColors(containerColor = bubbleBg),
                modifier = Modifier
                    .border(1.dp, bubbleBorderColor, RoundedCornerShape(12.dp))
                    .align(if (isUser) Alignment.End else Alignment.Start),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = message.content,
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 20.sp,
                        fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator(isUk: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.85f)
        ) {
            Text(
                text = "COGNITIVE COG-ENGINE PROCESSING",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFEAB308),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917)),
                modifier = Modifier.border(1.dp, Color(0xFFEAB308), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFEAB308),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isUk) {
                            "Модуль штучного інтелекту працює (Активовано аналітичний режим високого мислення)..."
                        } else {
                            "Reasoning Engine Active (High Thinking Mode processing)..."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFEAB308),
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
