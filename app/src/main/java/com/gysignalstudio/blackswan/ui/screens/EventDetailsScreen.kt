package com.gysignalstudio.blackswan.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gysignalstudio.blackswan.data.model.EventEntity
import com.gysignalstudio.blackswan.ui.localizedPublisher
import com.gysignalstudio.blackswan.ui.localizedSourceName
import com.gysignalstudio.blackswan.ui.viewmodel.OsintViewModel

private val brokenDetailEncodingMarkers = listOf(
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

private fun String.hasBrokenDetailEncoding(): Boolean =
    brokenDetailEncodingMarkers.any(::contains)

private fun localizedDetailText(isUk: Boolean, uk: String, en: String): String =
    if (isUk && uk.isNotBlank() && !uk.hasBrokenDetailEncoding()) uk else en

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsScreen(
    viewModel: OsintViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSource: () -> Unit
) {
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"

    val event by viewModel.selectedEvent.collectAsState()
    val sources by viewModel.allSources.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isUk) "ДЕТАЛІ ЗАПИСУ" else "EVENT OSINT DETAIL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        event?.let { e ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Category Banner
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = categoryLabel(e.category, isUk),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = localizedDetailText(isUk, e.titleUk, e.titleEn),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Meta Row (Date & Verification)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaInfoCard(
                        label = if (isUk) "ДАТА ЗАПИСУ" else "DOCUMENTED DATE",
                        value = e.date,
                        modifier = Modifier.weight(1f)
                    )
                    MetaInfoCard(
                        label = if (isUk) "СТАТУС ПЕРЕВІРКИ" else "VERIFICATION STATUS",
                        value = verificationLabel(e.verificationStatus, isUk),
                        valueColor = verificationColor(e.verificationStatus),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // MANDATORY SAFETY NOTE (page 10)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181B11)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF3B4016), RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Safety note",
                            tint = Color(0xFFEAB308),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isUk) {
                                "Локації є приблизними та навмисно узагальненими з міркувань безпеки та редакційної політики."
                            } else {
                                "Locations are approximate and intentionally generalized for safety and editorial reasons."
                            },
                            fontSize = 12.sp,
                            color = Color(0xFFEAB308),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = if (isUk) "ОПИС" else "DESCRIPTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = localizedDetailText(isUk, e.summaryUk, e.summaryEn),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Approximate Location Detail
                Text(
                    text = if (isUk) "ПРИБЛИЗНЕ РОЗТАШУВАННЯ" else "APPROXIMATE GEOGRAPHY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${localizedDetailText(isUk, e.approximateLocationLabelUk, e.approximateLocationLabelEn)} (~${e.radiusKm} km radius)",
                    fontSize = 13.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Actor Attribution Details
                Text(
                    text = if (isUk) "АТРИБУЦІЯ СУБ'ЄКТА" else "ACTOR ATTRIBUTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = e.actor.replace("_", " "),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                        if (e.actorNote.isNotEmpty()) {
                            Text(
                                text = e.actorNote,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Reference Sources (page 10, 28)
                Text(
                    text = if (isUk) "ВЕРИФІКОВАНІ ДЖЕРЕЛА" else "VERIFIED REFERENCE LIBRARY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                e.sources.split(",").forEach { srcId ->
                    val sourceObj = sources.find { it.sourceId == srcId.trim() }
                    sourceObj?.let { source ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.selectSource(source)
                                    onNavigateToSource()
                                },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Source,
                                    contentDescription = "Source Icon",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = localizedSourceName(source, isUk),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isUk) {
                                            "Видавець: ${localizedPublisher(source, true)} (рейтинг надійності: ${source.reliabilityScore}/5)"
                                        } else {
                                            "Publisher: ${localizedPublisher(source, false)} (Reliability Score: ${source.reliabilityScore}/5)"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetaInfoCard(
    label: String,
    value: String,
    valueColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
