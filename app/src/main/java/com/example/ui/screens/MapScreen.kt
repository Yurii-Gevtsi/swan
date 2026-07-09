package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.model.EventEntity
import com.example.data.model.RegionEntity
import com.example.data.model.MaritimeAreaEntity
import com.example.ui.viewmodel.OsintViewModel
import kotlin.math.sqrt

// Stylized vector-art coordinates for the seas and coastlines to draw base map
private val BLACK_SEA_COAST = listOf(
    Pair(41.0, 29.0), Pair(42.5, 27.5), Pair(43.5, 28.5), Pair(45.0, 29.7),
    Pair(46.4, 30.7), Pair(46.6, 31.5), Pair(45.8, 32.4), Pair(45.5, 32.7),
    Pair(44.6, 33.5), Pair(44.4, 34.0), Pair(45.0, 35.4), Pair(45.3, 36.5),
    Pair(45.4, 36.8), Pair(46.0, 38.0), Pair(47.2, 39.7), Pair(47.1, 37.6),
    Pair(46.7, 35.3), Pair(46.0, 34.8), Pair(45.3, 36.0), Pair(44.7, 37.8),
    Pair(44.3, 38.7), Pair(43.6, 39.7), Pair(43.0, 41.0), Pair(41.6, 41.6),
    Pair(41.3, 41.0), Pair(41.0, 39.7), Pair(41.5, 37.0), Pair(41.7, 36.0),
    Pair(42.0, 35.0), Pair(41.9, 33.0), Pair(41.2, 31.4), Pair(41.1, 29.5),
    Pair(41.0, 29.0)
)

private val BALTIC_SEA_COAST = listOf(
    Pair(54.0, 11.0), Pair(54.2, 12.1), Pair(53.9, 14.3), Pair(54.2, 16.0),
    Pair(54.4, 18.6), Pair(54.4, 19.8), Pair(54.7, 20.5), Pair(55.7, 21.1),
    Pair(56.3, 21.0), Pair(57.4, 21.5), Pair(57.0, 24.0), Pair(58.4, 24.4),
    Pair(58.3, 22.5), Pair(59.4, 24.8), Pair(59.5, 26.5), Pair(59.9, 29.0),
    Pair(59.9, 30.3), Pair(60.1, 30.2), Pair(60.4, 28.7), Pair(60.2, 26.9),
    Pair(60.2, 24.9), Pair(60.5, 22.3), Pair(61.5, 21.5), Pair(63.1, 21.6),
    Pair(65.0, 25.5), Pair(65.8, 24.1), Pair(65.6, 22.1), Pair(63.8, 20.3),
    Pair(62.6, 17.9), Pair(60.7, 17.3), Pair(59.3, 18.1), Pair(58.6, 16.2),
    Pair(56.2, 15.6), Pair(55.4, 13.8), Pair(55.6, 12.5), Pair(54.0, 11.0)
)

private val NORTHERN_COAST = listOf(
    Pair(69.7, 30.1), Pair(69.4, 31.0), Pair(68.9, 33.0), Pair(68.7, 35.0),
    Pair(68.1, 39.8), Pair(66.2, 40.0), Pair(66.5, 34.0), Pair(64.5, 34.8),
    Pair(64.0, 38.0), Pair(64.5, 40.5), Pair(65.8, 40.2), Pair(68.0, 44.0),
    Pair(68.1, 48.0), Pair(68.3, 53.0), Pair(69.5, 57.0), Pair(70.0, 60.0)
)

private val UKRAINE_BORDER = listOf(
    Pair(51.5, 23.5), Pair(52.0, 25.5), Pair(51.5, 28.0), Pair(52.3, 30.7),
    Pair(52.2, 33.2), Pair(50.8, 34.8), Pair(50.2, 36.3), Pair(49.8, 38.0),
    Pair(49.3, 40.1), Pair(47.8, 39.2), Pair(47.1, 38.3), Pair(46.0, 34.8),
    Pair(45.3, 32.5), Pair(44.5, 33.5), Pair(45.4, 36.5), Pair(46.2, 32.2),
    Pair(45.2, 29.7), Pair(46.5, 29.0), Pair(48.4, 26.6), Pair(48.0, 25.0),
    Pair(48.3, 22.8), Pair(49.0, 22.5), Pair(50.8, 24.1), Pair(51.5, 23.5)
)

private val BELARUS_BORDER = listOf(
    Pair(51.5, 23.5), Pair(52.0, 25.5), Pair(51.5, 28.0), Pair(52.3, 30.7),
    Pair(52.2, 33.2), Pair(53.5, 32.5), Pair(55.0, 31.0), Pair(55.8, 30.5),
    Pair(56.0, 28.0), Pair(54.8, 25.5), Pair(53.9, 23.5), Pair(51.5, 23.5)
)

private val NATO_EU_BORDER = listOf(
    Pair(54.0, 11.0), Pair(54.0, 14.0), Pair(54.5, 18.0), Pair(54.3, 20.0),
    Pair(55.8, 21.0), Pair(56.2, 21.0), Pair(57.5, 21.5), Pair(57.5, 24.3),
    Pair(59.4, 28.0), Pair(60.0, 29.0), Pair(61.0, 29.5), Pair(65.0, 29.5),
    Pair(69.0, 29.0)
)

private val REGION_POLYGONS = mapOf(
    "ru_rostov_oblast" to listOf(
        Pair(49.8, 41.5), Pair(49.5, 42.8), Pair(47.8, 43.8), Pair(46.2, 43.0),
        Pair(46.0, 41.5), Pair(47.0, 39.5), Pair(47.8, 39.2), Pair(49.6, 39.5)
    ),
    "ru_krasnodar_krai" to listOf(
        Pair(46.8, 38.0), Pair(46.5, 39.8), Pair(45.5, 41.5), Pair(43.8, 41.0),
        Pair(43.5, 39.8), Pair(44.3, 38.7), Pair(45.1, 36.6), Pair(45.3, 36.5)
    ),
    "ru_leningrad_oblast" to listOf(
        Pair(61.1, 29.0), Pair(60.8, 30.5), Pair(60.5, 33.0), Pair(60.0, 35.0),
        Pair(58.5, 33.5), Pair(58.4, 31.5), Pair(59.0, 28.5), Pair(59.4, 28.0)
    ),
    "ru_murmansk_oblast" to listOf(
        Pair(69.8, 32.5), Pair(69.0, 36.0), Pair(68.0, 40.5), Pair(66.2, 40.0),
        Pair(66.7, 34.0), Pair(67.2, 30.0), Pair(69.0, 29.0)
    ),
    "ru_belgorod_oblast" to listOf(
        Pair(51.3, 35.8), Pair(51.4, 36.6), Pair(51.1, 37.8), Pair(50.4, 38.5),
        Pair(49.8, 38.0), Pair(50.0, 37.2), Pair(50.2, 35.6)
    ),
    "ru_kursk_oblast" to listOf(
        Pair(52.4, 35.0), Pair(52.3, 36.2), Pair(52.1, 37.4), Pair(51.6, 38.4),
        Pair(51.1, 37.8), Pair(51.3, 35.8), Pair(51.3, 34.2)
    ),
    "ru_voronezh_oblast" to listOf(
        Pair(52.2, 38.0), Pair(52.3, 39.5), Pair(51.8, 41.5), Pair(50.7, 42.1),
        Pair(49.6, 41.0), Pair(49.9, 39.5), Pair(50.8, 38.5)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: OsintViewModel,
    onNavigateToEventDetails: () -> Unit,
    onNavigateToRegionDetails: () -> Unit,
    onNavigateToMaritimeDetails: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"

    val events by viewModel.filteredEvents.collectAsState()
    val regions by viewModel.allRegions.collectAsState()
    val maritimeAreas by viewModel.allMaritimeAreas.collectAsState()

    val selectedEvent by viewModel.selectedEvent.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val adsDisabled by viewModel.adsDisabled.collectAsState()

    // Map zoom action state
    var zoomTrigger by remember { mutableStateOf(0) }
    var zoomAction by remember { mutableStateOf<String?>(null) }

    // Dialog state for filters & legend
    var showFilterSheet by remember { mutableStateOf(false) }
    var showLegendDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "All" to "All",
        "INFRASTRUCTURE_DISRUPTION" to (if (isUk) "Інфраструктура" else "Infrastructure"),
        "FUEL_SUPPLY_DISRUPTION" to (if (isUk) "Паливо" else "Fuel"),
        "MARITIME_ASSET_DISRUPTION" to (if (isUk) "Морські активи" else "Maritime"),
        "SHADOW_FLEET_SANCTIONS" to (if (isUk) "Тіньовий флот" else "Shadow fleet"),
        "SANCTIONS_IMPACT" to (if (isUk) "Санкції" else "Sanctions"),
        "REGIONAL_FISCAL_STRESS" to (if (isUk) "Бюджетний стрес" else "Fiscal stress"),
        "INDUSTRIAL_DISRUPTION" to (if (isUk) "Промисловість" else "Industrial"),
        "OFFICIAL_STATEMENT" to (if (isUk) "Офіційні заяви" else "Statements")
    )

    val activeCategory by viewModel.selectedCategory.collectAsState()

    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val selectedMaritimeArea by viewModel.selectedMaritimeArea.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16)) // Custom premium dark canvas background
    ) {
        // Leaflet.js and OpenStreetMap real map view
        LeafletMapView(
            events = events,
            regions = regions,
            maritimeAreas = maritimeAreas,
            selectedEventId = selectedEvent?.id,
            selectedRegionId = selectedRegion?.regionId,
            selectedMaritimeId = selectedMaritimeArea?.maritimeAreaId,
            isUk = isUk,
            onEventClick = { eventId ->
                val ev = events.find { it.id == eventId }
                if (ev != null) {
                    viewModel.selectEvent(ev)
                }
            },
            onRegionClick = { regionId ->
                val rg = regions.find { it.regionId == regionId }
                if (rg != null) {
                    viewModel.selectRegion(rg)
                    onNavigateToRegionDetails()
                }
            },
            onMaritimeAreaClick = { areaId ->
                val ar = maritimeAreas.find { it.maritimeAreaId == areaId }
                if (ar != null) {
                    viewModel.selectMaritimeArea(ar)
                    onNavigateToMaritimeDetails()
                }
            },
            zoomTrigger = zoomTrigger,
            zoomAction = zoomAction,
            onZoomActionConsumed = { zoomAction = null },
            modifier = Modifier.fillMaxSize()
        )

        // Top Search Bar & Category Chips (page 9)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = {
                        Text(
                            text = if (isUk) "Пошук OSINT записів..." else "Search OSINT logs...",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Detail Filter Trigger
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filters",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Chips List (page 9)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { (id, label) ->
                    val isSelected = (id == "All" && activeCategory == null) || (activeCategory == id)
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) Color.White else Color(0xFF0F172A), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                            .clickable {
                                if (id == "All") {
                                    viewModel.selectedCategory.value = null
                                } else {
                                    viewModel.selectedCategory.value = id
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF020617) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Map Control Floating Buttons (Zoom In, Zoom Out, Legend, Reset)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    zoomAction = "IN"
                    zoomTrigger++
                },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In")
            }

            Spacer(modifier = Modifier.height(8.dp))

            FloatingActionButton(
                onClick = {
                    zoomAction = "OUT"
                    zoomTrigger++
                },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            Spacer(modifier = Modifier.height(8.dp))

            FloatingActionButton(
                onClick = {
                    zoomAction = "RESET"
                    zoomTrigger++
                },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset View")
            }

            Spacer(modifier = Modifier.height(8.dp))

            FloatingActionButton(
                onClick = { showLegendDialog = true },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Legend")
            }
        }

        // Selected Event Preview Bottom Panel (page 9)
        selectedEvent?.let { event ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (adsDisabled) 16.dp else 70.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .clickable { onNavigateToEventDetails() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (event.category) {
                                "ENERGY_EXPORT_DISRUPTION" -> Color(0xFFEF4444)
                                "SHADOW_FLEET_SANCTIONS" -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(dotColor, RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = event.category.replace("_", " "),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = event.date,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isUk) event.titleUk else event.titleEn,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isUk) event.approximateLocationLabelUk else event.approximateLocationLabelEn,
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Source,
                                contentDescription = "Sources",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${event.sources.split(",").size} ${if (isUk) "джерела" else "sources"}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (isUk) "Детальніше →" else "Details →",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Mock Bottom Banner Ad if ads are enabled (page 23)
        if (!adsDisabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF475569), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(text = "Ad", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isUk) "Купуйте Ad-Free в налаштуваннях" else "Remove ads in Settings for complete ad-free experience",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // --- FILTER PANEL BOTTOM SHEET DIALOG (page 9) ---
        if (showFilterSheet) {
            Dialog(onDismissRequest = { showFilterSheet = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isUk) "ДЕТАЛЬНІ ФІЛЬТРИ" else "DETAILED FILTERS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { showFilterSheet = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Theater Filter
                        Text(
                            text = if (isUk) "Театр дій" else "Theater of Action",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val theaterList = listOf("RUSSIA_INTERNAL", "BLACK_SEA", "BALTIC_SEA")
                            theaterList.forEach { th ->
                                val active = viewModel.selectedTheater.collectAsState().value == th
                                Box(
                                    modifier = Modifier
                                        .background(if (active) Color.White else Color(0xFF1E293B), RoundedCornerShape(20.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                                        .clickable { viewModel.selectedTheater.value = if (active) null else th }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = th.replace("_", " "),
                                        color = if (active) Color(0xFF020617) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Severity Filter
                        Text(
                            text = if (isUk) "Рівень серйозності" else "Severity Level",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val severityList = listOf("LOW", "MEDIUM", "HIGH", "SYSTEMIC")
                            severityList.forEach { sev ->
                                val active = viewModel.selectedSeverity.collectAsState().value == sev
                                Box(
                                    modifier = Modifier
                                        .background(if (active) Color.White else Color(0xFF1E293B), RoundedCornerShape(20.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                                        .clickable { viewModel.selectedSeverity.value = if (active) null else sev }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = sev,
                                        color = if (active) Color(0xFF020617) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.resetFilters()
                                    showFilterSheet = false
                                },
                                modifier = Modifier.weight(1f),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFEF4444)))
                                )
                            ) {
                                Text(text = if (isUk) "Скинути" else "Reset", color = Color(0xFFEF4444))
                            }

                            Button(
                                onClick = { showFilterSheet = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF020617))
                            ) {
                                Text(text = if (isUk) "Застосувати" else "Apply")
                            }
                        }
                    }
                }
            }
        }

        // --- LEGEND DIALOG ---
        if (showLegendDialog) {
            Dialog(onDismissRequest = { showLegendDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isUk) "ЛЕГЕНДА КАРТИ" else "MAP LEGEND",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { showLegendDialog = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LegendRow(color = Color(0xFFEF4444), label = if (isUk) "Збій енергетичного експорту" else "Energy export disruption")
                        LegendRow(color = Color(0xFFF59E0B), label = if (isUk) "Санкції проти тіньового флоту" else "Shadow fleet sanctions")
                        LegendRow(color = Color(0xFF10B981), label = if (isUk) "Логістичний чи портовий збій" else "Port or logistics disruption")
                        LegendRow(color = Color(0xFF38BDF8), label = if (isUk) "Морська регіональна зона" else "Maritime regional zone")

                        Spacer(modifier = Modifier.height(12.dp))

                        Divider(color = Color(0xFF334155))

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isUk) {
                                "Усі маркери та зони відображають узагальнені й неточні координати з метою безпеки."
                            } else {
                                "All boundaries and zones represent generalized and inaccurate locations for security purposes."
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendRow(color: Color, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .border(1.dp, color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = Color.White, fontSize = 13.sp)
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MapEventDto(
    val id: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
    val category: String,
    val titleEn: String,
    val titleUk: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MapRegionDto(
    val regionId: String,
    val lat: Double,
    val lng: Double,
    val defaultRadiusKm: Int,
    val fuelStressStatus: String,
    val fiscalStressStatus: String,
    val nameEn: String,
    val nameUk: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MapMaritimeDto(
    val maritimeAreaId: String,
    val lat: Double,
    val lng: Double,
    val defaultRadiusKm: Int,
    val theater: String,
    val nameEn: String,
    val nameUk: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapView(
    events: List<EventEntity>,
    regions: List<RegionEntity>,
    maritimeAreas: List<MaritimeAreaEntity>,
    selectedEventId: String?,
    selectedRegionId: String?,
    selectedMaritimeId: String?,
    isUk: Boolean,
    onEventClick: (String) -> Unit,
    onRegionClick: (String) -> Unit,
    onMaritimeAreaClick: (String) -> Unit,
    zoomTrigger: Int,
    zoomAction: String?,
    onZoomActionConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val html = remember { getMapHtml() }

    val moshi = remember { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    val eventsAdapter = remember { moshi.adapter<List<MapEventDto>>(Types.newParameterizedType(List::class.java, MapEventDto::class.java)) }
    val regionsAdapter = remember { moshi.adapter<List<MapRegionDto>>(Types.newParameterizedType(List::class.java, MapRegionDto::class.java)) }
    val maritimeAdapter = remember { moshi.adapter<List<MapMaritimeDto>>(Types.newParameterizedType(List::class.java, MapMaritimeDto::class.java)) }

    val eventsDto = remember(events) {
        events.map { MapEventDto(it.id, it.lat, it.lng, it.radiusKm, it.category, it.titleEn, it.titleUk) }
    }
    val regionsDto = remember(regions) {
        regions.map { MapRegionDto(it.regionId, it.lat, it.lng, it.defaultRadiusKm, it.fuelStressStatus, it.fiscalStressStatus, it.nameEn, it.nameUk) }
    }
    val maritimeDto = remember(maritimeAreas) {
        maritimeAreas.map { MapMaritimeDto(it.maritimeAreaId, it.lat, it.lng, it.defaultRadiusKm, it.theater, it.nameEn, it.nameUk) }
    }

    val eventsJson = remember(eventsDto) { eventsAdapter.toJson(eventsDto) }
    val regionsJson = remember(regionsDto) { regionsAdapter.toJson(regionsDto) }
    val maritimeJson = remember(maritimeDto) { maritimeAdapter.toJson(maritimeDto) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(eventsJson, regionsJson, maritimeJson, selectedEventId, selectedRegionId, selectedMaritimeId, isUk, isLoaded) {
        val webView = webViewRef
        if (webView != null && isLoaded) {
            val escapedEvents = eventsJson.replace("'", "\\'")
            val escapedRegions = regionsJson.replace("'", "\\'")
            val escapedMaritime = maritimeJson.replace("'", "\\'")

            val js = "setMapData('$escapedEvents', '$escapedRegions', '$escapedMaritime', " +
                    "${if (selectedEventId != null) "'$selectedEventId'" else "null"}, " +
                    "${if (selectedRegionId != null) "'$selectedRegionId'" else "null"}, " +
                    "${if (selectedMaritimeId != null) "'$selectedMaritimeId'" else "null"}, $isUk);"
            webView.evaluateJavascript(js, null)
        }
    }

    LaunchedEffect(zoomTrigger) {
        val webView = webViewRef
        if (webView != null && isLoaded && zoomAction != null) {
            when (zoomAction) {
                "IN" -> webView.evaluateJavascript("zoomIn();", null)
                "OUT" -> webView.evaluateJavascript("zoomOut();", null)
                "RESET" -> webView.evaluateJavascript("resetView();", null)
            }
            onZoomActionConsumed()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef = this
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }

                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onEventClick(eventId: String) {
                        onEventClick(eventId)
                    }

                    @android.webkit.JavascriptInterface
                    fun onRegionClick(regionId: String) {
                        onRegionClick(regionId)
                    }

                    @android.webkit.JavascriptInterface
                    fun onMaritimeAreaClick(areaId: String) {
                        onMaritimeAreaClick(areaId)
                    }
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoaded = true
                    }
                }

                loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier
    )
}

private fun getMapHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    background-color: #090D16;
                }
                .leaflet-container {
                    background: #090D16;
                }
                .leaflet-bar a {
                    background-color: #0f172a !important;
                    color: #ffffff !important;
                    border: 1px solid #334155 !important;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var REGION_POLYGONS = {
                    "ru_rostov_oblast": [
                        [49.8, 41.5], [49.5, 42.8], [47.8, 43.8], [46.2, 43.0],
                        [46.0, 41.5], [47.0, 39.5], [47.8, 39.2], [49.6, 39.5]
                    ],
                    "ru_krasnodar_krai": [
                        [46.8, 38.0], [46.5, 39.8], [45.5, 41.5], [43.8, 41.0],
                        [43.5, 39.8], [44.3, 38.7], [45.1, 36.6], [45.3, 36.5]
                    ],
                    "ru_leningrad_oblast": [
                        [61.1, 29.0], [60.8, 30.5], [60.5, 33.0], [60.0, 35.0],
                        [58.5, 33.5], [58.4, 31.5], [59.0, 28.5], [59.4, 28.0]
                    ],
                    "ru_murmansk_oblast": [
                        [69.8, 32.5], [69.0, 36.0], [68.0, 40.5], [66.2, 40.0],
                        [66.7, 34.0], [67.2, 30.0], [69.0, 29.0]
                    ],
                    "ru_belgorod_oblast": [
                        [51.3, 35.8], [51.4, 36.6], [51.1, 37.8], [50.4, 38.5],
                        [49.8, 38.0], [50.0, 37.2], [50.2, 35.6]
                    ],
                    "ru_kursk_oblast": [
                        [52.4, 35.0], [52.3, 36.2], [52.1, 37.4], [51.6, 38.4],
                        [51.1, 37.8], [51.3, 35.8], [51.3, 34.2]
                    ],
                    "ru_voronezh_oblast": [
                        [52.2, 38.0], [52.3, 39.5], [51.8, 41.5], [50.7, 42.1],
                        [49.6, 41.0], [49.9, 39.5], [50.8, 38.5]
                    ]
                };

                var UKRAINE_BORDER = [
                    [51.5, 23.5], [52.0, 25.5], [51.5, 28.0], [52.3, 30.7],
                    [52.2, 33.2], [50.8, 34.8], [50.2, 36.3], [49.8, 38.0],
                    [49.3, 40.1], [47.8, 39.2], [47.1, 38.3], [46.0, 34.8],
                    [45.3, 32.5], [44.5, 33.5], [45.4, 36.5], [46.2, 32.2],
                    [45.2, 29.7], [46.5, 29.0], [48.4, 26.6], [48.0, 25.0],
                    [48.3, 22.8], [49.0, 22.5], [50.8, 24.1], [51.5, 23.5]
                ];

                var BELARUS_BORDER = [
                    [51.5, 23.5], [52.0, 25.5], [51.5, 28.0], [52.3, 30.7],
                    [52.2, 33.2], [53.5, 32.5], [55.0, 31.0], [55.8, 30.5],
                    [56.0, 28.0], [54.8, 25.5], [53.9, 23.5], [51.5, 23.5]
                ];

                var NATO_EU_BORDER = [
                    [54.0, 11.0], [54.0, 14.0], [54.5, 18.0], [54.3, 20.0],
                    [55.8, 21.0], [56.2, 21.0], [57.5, 21.5], [57.5, 24.3],
                    [59.4, 28.0], [60.0, 29.0], [61.0, 29.5], [65.0, 29.5],
                    [69.0, 29.0]
                ];

                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false
                }).setView([55.0, 37.0], 5);

                L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
                    maxZoom: 18,
                    minZoom: 3
                }).addTo(map);

                L.polyline(NATO_EU_BORDER, {
                    color: '#64748B',
                    weight: 2,
                    dashArray: '10, 10',
                    interactive: false
                }).addTo(map);

                L.polygon(UKRAINE_BORDER, {
                    color: '#3B82F6',
                    fillColor: '#14243C',
                    fillOpacity: 0.25,
                    weight: 2.5,
                    interactive: false
                }).addTo(map);

                L.polygon(BELARUS_BORDER, {
                    color: '#475569',
                    fillColor: '#192239',
                    fillOpacity: 0.25,
                    weight: 1.5,
                    interactive: false
                }).addTo(map);

                var eventMarkers = {};
                var regionPolygons = {};
                var maritimeCircles = {};

                function zoomIn() { map.zoomIn(); }
                function zoomOut() { map.zoomOut(); }
                function resetView() { map.setView([55.0, 37.0], 5); }

                function clearMap() {
                    for (var id in eventMarkers) {
                        map.removeLayer(eventMarkers[id].circle);
                        map.removeLayer(eventMarkers[id].marker);
                    }
                    for (var id in regionPolygons) {
                        map.removeLayer(regionPolygons[id]);
                    }
                    for (var id in maritimeCircles) {
                        map.removeLayer(maritimeCircles[id]);
                    }
                    eventMarkers = {};
                    regionPolygons = {};
                    maritimeCircles = {};
                }

                function selectRegion(regionId) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onRegionClick(regionId);
                    }
                }

                function selectEvent(eventId) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onEventClick(eventId);
                    }
                }

                function selectMaritimeArea(areaId) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onMaritimeAreaClick(areaId);
                    }
                }

                function setMapData(eventsJson, regionsJson, maritimeJson, selectedEventId, selectedRegionId, selectedMaritimeId, isUk) {
                    clearMap();

                    var maritime = JSON.parse(maritimeJson);
                    maritime.forEach(function(area) {
                        var color = "#0284c7";
                        if (area.theater === "BLACK_SEA") color = "#0284c7";
                        else if (area.theater === "AZOV_SEA") color = "#0369a1";
                        else if (area.theater === "BALTIC_SEA") color = "#0ea5e9";

                        var isSelected = area.maritimeAreaId === selectedMaritimeId;
                        var circle = L.circle([area.lat, area.lng], {
                            color: color,
                            fillColor: color,
                            fillOpacity: isSelected ? 0.3 : 0.1,
                            radius: area.defaultRadiusKm * 1000,
                            weight: isSelected ? 3.5 : 2,
                            dashArray: '5, 5'
                        }).addTo(map);

                        circle.bindTooltip(isUk ? area.nameUk : area.nameEn, { permanent: false, direction: 'top' });

                        circle.on('click', function() {
                            selectMaritimeArea(area.maritimeAreaId);
                        });

                        maritimeCircles[area.maritimeAreaId] = circle;

                        if (isSelected) {
                            map.setView([area.lat, area.lng], map.getZoom());
                        }
                    });

                    var regions = JSON.parse(regionsJson);
                    regions.forEach(function(region) {
                        var color = "#10b981";
                        if (region.fuelStressStatus === "HIGH" || region.fiscalStressStatus === "HIGH") {
                            color = "#ef4444";
                        } else if (region.fuelStressStatus === "MEDIUM" || region.fiscalStressStatus === "MEDIUM") {
                            color = "#f59e0b";
                        }

                        var polygonCoords = REGION_POLYGONS[region.regionId];
                        var isSelected = region.regionId === selectedRegionId;
                        var layer;

                        if (polygonCoords) {
                            layer = L.polygon(polygonCoords, {
                                color: color,
                                fillColor: color,
                                fillOpacity: isSelected ? 0.5 : 0.25,
                                weight: isSelected ? 3 : 1.5
                            }).addTo(map);
                        } else {
                            layer = L.circle([region.lat, region.lng], {
                                color: color,
                                fillColor: color,
                                fillOpacity: isSelected ? 0.5 : 0.25,
                                radius: region.defaultRadiusKm * 1000,
                                weight: isSelected ? 3 : 1.5
                            }).addTo(map);
                        }

                        layer.bindTooltip(isUk ? region.nameUk : region.nameEn, { permanent: false, direction: 'center' });

                        layer.on('click', function() {
                            selectRegion(region.regionId);
                        });

                        regionPolygons[region.regionId] = layer;

                        if (isSelected) {
                            map.setView([region.lat, region.lng], map.getZoom());
                        }
                    });

                    var evs = JSON.parse(eventsJson);
                    evs.forEach(function(event) {
                        var pulseColor = "#ef4444";
                        if (event.category === "ENERGY_EXPORT_DISRUPTION") pulseColor = "#ef4444";
                        else if (event.category === "SHADOW_FLEET_SANCTIONS") pulseColor = "#f59e0b";
                        else if (event.category === "MARITIME_REGIONAL_SECURITY") pulseColor = "#0ea5e9";

                        var isSelected = event.id === selectedEventId;

                        var circle = L.circle([event.lat, event.lng], {
                            color: pulseColor,
                            fillColor: pulseColor,
                            fillOpacity: isSelected ? 0.4 : 0.15,
                            radius: event.radiusKm * 1000,
                            weight: isSelected ? 2.5 : 1
                        }).addTo(map);

                        var marker = L.circleMarker([event.lat, event.lng], {
                            radius: isSelected ? 8 : 5,
                            fillColor: isSelected ? '#fef08a' : '#ffffff',
                            fillOpacity: 1,
                            color: pulseColor,
                            weight: 2
                        }).addTo(map);

                        marker.bindTooltip(isUk ? event.titleUk : event.titleEn, { permanent: false });

                        var selectFn = function() {
                            selectEvent(event.id);
                        };

                        circle.on('click', selectFn);
                        marker.on('click', selectFn);

                        eventMarkers[event.id] = { circle: circle, marker: marker };

                        if (isSelected) {
                            map.setView([event.lat, event.lng], map.getZoom());
                        }
                    });
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
