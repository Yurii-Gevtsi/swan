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
import com.example.data.model.EventEntity
import com.example.data.model.RegionEntity
import com.example.data.model.MaritimeAreaEntity
import com.example.ui.viewmodel.OsintViewModel
import kotlin.math.sqrt

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

    // Map Viewport state (Zoom & Pan)
    var zoom by remember { mutableStateOf(4.5f) } // defaultZoom 4.2-4.8
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16)) // Custom premium dark canvas background
    ) {
        // Parent Transformable State to handle Pan and Zoom gestures (page 5-6)
        val state = rememberTransformableState { zoomChange, panChange, _ ->
            zoom = (zoom * zoomChange).coerceIn(3.0f, 6.0f) // minZoom 3.0, maxZoom 6.0
            panX += panChange.x
            panY += panChange.y
        }

        // Custom Map Canvas Rendering
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state)
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        // Detect taps on events, regions or seas
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()

                        // Check events
                        var clickedEvent: EventEntity? = null
                        var minDistance = Float.MAX_VALUE

                        events.forEach { event ->
                            val mapOffset = getCanvasOffset(event.lat, event.lng, width, height, zoom, panX, panY)
                            val dist = sqrt((tapOffset.x - mapOffset.x) * (tapOffset.x - mapOffset.x) + (tapOffset.y - mapOffset.y) * (tapOffset.y - mapOffset.y))
                            if (dist < 40f && dist < minDistance) {
                                clickedEvent = event
                                minDistance = dist
                            }
                        }

                        if (clickedEvent != null) {
                            viewModel.selectEvent(clickedEvent!!)
                        } else {
                            // Check regions
                            var clickedRegion: RegionEntity? = null
                            regions.forEach { region ->
                                val mapOffset = getCanvasOffset(region.lat, region.lng, width, height, zoom, panX, panY)
                                val dist = sqrt((tapOffset.x - mapOffset.x) * (tapOffset.x - mapOffset.x) + (tapOffset.y - mapOffset.y) * (tapOffset.y - mapOffset.y))
                                if (dist < 50f && dist < minDistance) {
                                    clickedRegion = region
                                    minDistance = dist
                                }
                            }
                            if (clickedRegion != null) {
                                viewModel.selectRegion(clickedRegion!!)
                                onNavigateToRegionDetails()
                            }
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw European / Neighborhood low-detail context background (page 3-4)
            // Draw a neutral border for Ukraine as "Excluded from dataset" (Option B, page 3)
            val ukraineOffset = getCanvasOffset(48.3, 31.1, canvasWidth, canvasHeight, zoom, panX, panY)
            drawCircle(
                color = Color(0xFF1E293B),
                radius = 120f * (zoom / 4.5f),
                center = ukraineOffset,
                style = Stroke(width = 1.5f, miter = 1f)
            )
            // Neutral mask tag for Ukraine
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(ukraineOffset.x - 70f, ukraineOffset.y - 20f),
                size = Size(140f, 40f),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // 2. Draw Seas and Maritime general zones (page 12)
            maritimeAreas.forEach { area ->
                val areaOffset = getCanvasOffset(area.lat, area.lng, canvasWidth, canvasHeight, zoom, panX, panY)
                val colorTheme = when (area.theater) {
                    "BLACK_SEA" -> Color(0xFF0284C7)
                    "AZOV_SEA" -> Color(0xFF0369A1)
                    "BALTIC_SEA" -> Color(0xFF0EA5E9)
                    else -> Color(0xFF38BDF8)
                }
                // Sea boundary indication (represented as soft glowing dashed circles)
                drawCircle(
                    color = colorTheme.copy(alpha = 0.15f),
                    radius = (area.defaultRadiusKm.toFloat() * 1.2f) * (zoom / 4.5f),
                    center = areaOffset
                )
                drawCircle(
                    color = colorTheme.copy(alpha = 0.4f),
                    radius = (area.defaultRadiusKm.toFloat() * 1.2f) * (zoom / 4.5f),
                    center = areaOffset,
                    style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )
            }

            // 3. Draw Russian Federal Subjects / Regions as Choropleths (page 4, 18)
            regions.forEach { region ->
                val regionOffset = getCanvasOffset(region.lat, region.lng, canvasWidth, canvasHeight, zoom, panX, panY)

                // Define Choropleth color based on stress status (page 4, 9)
                val choroplethColor = when {
                    region.fuelStressStatus == "HIGH" || region.fiscalStressStatus == "HIGH" -> Color(0xFF991B1B) // Deep Crimson
                    region.fuelStressStatus == "MEDIUM" || region.fiscalStressStatus == "MEDIUM" -> Color(0xFF9A3412) // Dark Amber
                    else -> Color(0xFF1E293B) // Slate Dark
                }

                // Draw region area choropleth circle
                drawCircle(
                    color = choroplethColor.copy(alpha = 0.55f),
                    radius = (region.defaultRadiusKm.toFloat() * 1.1f) * (zoom / 4.5f),
                    center = regionOffset
                )
                drawCircle(
                    color = Color(0xFF334155).copy(alpha = 0.7f),
                    radius = (region.defaultRadiusKm.toFloat() * 1.1f) * (zoom / 4.5f),
                    center = regionOffset,
                    style = Stroke(width = 1.5f)
                )
            }

            // 4. Draw Event Approximate Zones (represented as circles, page 4, 10)
            events.forEach { event ->
                val eventOffset = getCanvasOffset(event.lat, event.lng, canvasWidth, canvasHeight, zoom, panX, panY)

                // Circle proportional to radius (min 50km, page 10)
                val circleRadius = event.radiusKm.toFloat() * (zoom / 4.5f)

                val pulseColor = when (event.category) {
                    "ENERGY_EXPORT_DISRUPTION" -> Color(0xFFEF4444) // Neon Red
                    "SHADOW_FLEET_SANCTIONS" -> Color(0xFFF59E0B) // Amber
                    "PORT_LOGISTICS_DISRUPTION" -> Color(0xFF10B981) // Emerald
                    else -> Color(0xFFD946EF) // Magenta
                }

                // Draw glowing outer area
                drawCircle(
                    color = pulseColor.copy(alpha = 0.18f),
                    radius = circleRadius,
                    center = eventOffset
                )
                drawCircle(
                    color = pulseColor.copy(alpha = 0.5f),
                    radius = circleRadius,
                    center = eventOffset,
                    style = Stroke(width = 1.5f)
                )

                // Central high-contrast marker point
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = eventOffset
                )
                drawCircle(
                    color = pulseColor,
                    radius = 5f,
                    center = eventOffset
                )
            }
        }

        // Map Label Overlays (Clean text labels rendered precisely over Canvas points)
        Box(modifier = Modifier.fillMaxSize()) {
            // Ukraine label
            BoxWithConstraints {
                val ukOffset = getCanvasOffset(48.3, 31.1, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), zoom, panX, panY)
                Text(
                    text = if (isUk) "УКРАЇНА\n(ПОЗА НАБОРОМ ДАНИХ)" else "UKRAINE\n(EXCLUDED FROM DATASET)",
                    color = Color(0xFF64748B),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(
                        x = (ukOffset.x / density).dp - 65.dp,
                        y = (ukOffset.y / density).dp - 15.dp
                    )
                )
            }

            // Russian Region Labels (Rendered only on suitable zoom levels)
            if (zoom >= 4.0f) {
                regions.forEach { region ->
                    BoxWithConstraints {
                        val offset = getCanvasOffset(region.lat, region.lng, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), zoom, panX, panY)
                        Text(
                            text = if (isUk) region.nameUk else region.nameEn,
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .offset(
                                    x = (offset.x / density).dp - 50.dp,
                                    y = (offset.y / density).dp - 25.dp
                                )
                                .width(100.dp)
                        )
                    }
                }
            }

            // Seas Labels
            maritimeAreas.forEach { area ->
                BoxWithConstraints {
                    val offset = getCanvasOffset(area.lat, area.lng, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), zoom, panX, panY)
                    Text(
                        text = if (isUk) area.nameUk else area.nameEn,
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.offset(
                            x = (offset.x / density).dp - 40.dp,
                            y = (offset.y / density).dp - 8.dp
                        )
                    )
                }
            }
        }

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
                onClick = { zoom = (zoom + 0.3f).coerceAtMost(6.0f) },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In")
            }

            Spacer(modifier = Modifier.height(8.dp))

            FloatingActionButton(
                onClick = { zoom = (zoom - 0.3f).coerceAtLeast(3.0f) },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out")
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

// Convert Lat Lng to Canvas Grid coordinates with zoom/pan
fun getCanvasOffset(
    lat: Double,
    lng: Double,
    width: Float,
    height: Float,
    zoom: Float,
    panX: Float,
    panY: Float
): Offset {
    // Limits of our strategic map (covers European Russia & adjacent seas)
    // Longitude from 10.0 (Western Europe) to 60.0 (Ural Mountains)
    // Latitude from 72.0 (Murmansk) to 42.0 (Black Sea)
    val minLng = 10.0
    val maxLng = 60.0
    val minLat = 42.0
    val maxLat = 72.0

    val xPct = (lng - minLng) / (maxLng - minLng)
    val yPct = (maxLat - lat) / (maxLat - minLat)

    val x = xPct.toFloat() * width
    val y = yPct.toFloat() * height

    val cx = width / 2f
    val cy = height / 2f

    // Scale from center & add pan
    val zx = (x - cx) * zoom + cx + panX
    val zy = (y - cy) * zoom + cy + panY

    return Offset(zx, zy)
}
