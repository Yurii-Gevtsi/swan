package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RegionEntity
import com.example.data.model.MaritimeAreaEntity
import com.example.data.model.EventEntity
import com.example.ui.viewmodel.OsintViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreasScreen(
    viewModel: OsintViewModel,
    onNavigateToEventDetails: () -> Unit
) {
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"

    val regions by viewModel.allRegions.collectAsState()
    val maritimeAreas by viewModel.allMaritimeAreas.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Regions, 1 = Maritime

    var selectedRegionId by remember { mutableStateOf<String?>(null) }
    var selectedMaritimeId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isUk) "РЕГІОНИ ТА ЗОНИ" else "REGIONS & WATERWAYS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
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
            // Tab Selector
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = Color.White
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = {
                        activeTab = 0
                        selectedRegionId = null
                        selectedMaritimeId = null
                    },
                    text = { Text(text = if (isUk) "Російські регіони" else "Russian Regions") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = {
                        activeTab = 1
                        selectedRegionId = null
                        selectedMaritimeId = null
                    },
                    text = { Text(text = if (isUk) "Морські зони" else "Maritime Areas") }
                )
            }

            // Detail Panel or List View
            if (activeTab == 0) {
                // REGIONS LIST / DETAILS (page 12)
                if (selectedRegionId == null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(12.dp)) }

                        items(regions) { region ->
                            RegionCard(
                                region = region,
                                isUk = isUk,
                                onClick = { selectedRegionId = region.regionId }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                } else {
                    val region = regions.find { it.regionId == selectedRegionId }
                    region?.let { r ->
                        RegionDetailView(
                            region = r,
                            isUk = isUk,
                            allEvents = allEvents,
                            onBack = { selectedRegionId = null },
                            onEventClick = { event ->
                                viewModel.selectEvent(event)
                                onNavigateToEventDetails()
                            }
                        )
                    }
                }
            } else {
                // MARITIME AREAS LIST / DETAILS (page 12)
                if (selectedMaritimeId == null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(12.dp)) }

                        items(maritimeAreas) { area ->
                            MaritimeAreaCard(
                                area = area,
                                isUk = isUk,
                                onClick = { selectedMaritimeId = area.maritimeAreaId }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                } else {
                    val area = maritimeAreas.find { it.maritimeAreaId == selectedMaritimeId }
                    area?.let { a ->
                        MaritimeAreaDetailView(
                            area = a,
                            isUk = isUk,
                            allEvents = allEvents,
                            onBack = { selectedMaritimeId = null },
                            onEventClick = { event ->
                                viewModel.selectEvent(event)
                                onNavigateToEventDetails()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegionCard(
    region: RegionEntity,
    isUk: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = region.federalDistrictId.replace("ru_", "").replace("_", " ").uppercase(),
                fontSize = 9.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isUk) region.nameUk else region.nameEn,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = "View", tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StressIndicatorChip(label = if (isUk) "Паливний стрес" else "Fuel Stress", status = region.fuelStressStatus)
                StressIndicatorChip(label = if (isUk) "Бюджетний стрес" else "Fiscal Stress", status = region.fiscalStressStatus)
            }
        }
    }
}

@Composable
fun MaritimeAreaCard(
    area: MaritimeAreaEntity,
    isUk: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = area.theater.replace("_", " ").uppercase(),
                fontSize = 9.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isUk) area.nameUk else area.nameEn,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = "View", tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(text = if (isUk) "АКТИВИ" else "ASSETS", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                    Text(text = "${area.assetEventsCount} records", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column {
                    Text(text = if (isUk) "САНКЦІЇ" else "SANCTIONS", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                    Text(text = "${area.sanctionsEventsCount} records", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun StressIndicatorChip(label: String, status: String) {
    val chipColor = when (status) {
        "HIGH" -> Color(0xFF991B1B)
        "MEDIUM" -> Color(0xFF9A3412)
        else -> Color(0xFF1E293B)
    }
    Row(
        modifier = Modifier
            .background(chipColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (status == "HIGH" || status == "MEDIUM") Color.White else Color(0xFF64748B), RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "$label: $status", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RegionDetailView(
    region: RegionEntity,
    isUk: Boolean,
    allEvents: List<EventEntity>,
    onBack: () -> Unit,
    onEventClick: (EventEntity) -> Unit
) {
    val relatedEvents = allEvents.filter { it.regionId == region.regionId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isUk) region.nameUk else region.nameEn,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Federal District & Coordinates (Approximate)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(label = if (isUk) "Федеральний округ" else "Federal District", value = region.federalDistrictId.replace("ru_", "").replace("_", " ").uppercase())
                DetailRow(label = if (isUk) "Приблизні координати" else "Approximate Coordinates", value = "Lat: ${region.lat}, Lng: ${region.lng}")
                DetailRow(label = if (isUk) "Головні міста" else "Major Cities", value = region.majorCities)
                DetailRow(label = if (isUk) "Останнє оновлення" else "Last Updated", value = region.lastUpdated)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stress Status
        Text(text = if (isUk) "ІНДИКАТОРИ СТРЕСУ РЕГІОНУ" else "REGIONAL STRESS INDICATORS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StressBox(label = if (isUk) "Паливний стрес" else "Fuel Stress", status = region.fuelStressStatus, modifier = Modifier.weight(1f))
            StressBox(label = if (isUk) "Бюджетний стрес" else "Fiscal Stress", status = region.fiscalStressStatus, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Related documented events
        Text(text = if (isUk) "ДОКУМЕНТОВАНІ ПОДІЇ В РЕГІОНІ" else "DOCUMENTED REGIONAL LOGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(8.dp))

        if (relatedEvents.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = if (isUk) "Не зафіксовано активних подій" else "No active regional events documented.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        } else {
            relatedEvents.forEach { event ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .clickable { onEventClick(event) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = event.date, fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(text = if (isUk) event.titleUk else event.titleEn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        }
                        Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = "View", tint = Color(0xFF475569), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MaritimeAreaDetailView(
    area: MaritimeAreaEntity,
    isUk: Boolean,
    allEvents: List<EventEntity>,
    onBack: () -> Unit,
    onEventClick: (EventEntity) -> Unit
) {
    val relatedEvents = allEvents.filter { it.maritimeAreaId == area.maritimeAreaId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isUk) area.nameUk else area.nameEn,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Maritime Metadata (page 12)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(label = if (isUk) "Театр дій" else "Theater Sector", value = area.theater.replace("_", " "))
                DetailRow(label = if (isUk) "Приблизні координати" else "Approximate Coordinates", value = "Lat: ${area.lat}, Lng: ${area.lng}")
                DetailRow(label = if (isUk) "Останнє оновлення" else "Last Updated", value = area.lastUpdated)
                DetailRow(label = if (isUk) "Загальна кількість подій" else "Total Event Count", value = "${area.eventCount}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Related documented events
        Text(text = if (isUk) "ЗАФІКСОВАНІ ПОДІЇ В АКВАТОРІЇ" else "DOCUMENTED WATERWAY RECORDS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(8.dp))

        if (relatedEvents.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = if (isUk) "Не зафіксовано активних подій" else "No active maritime events documented.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        } else {
            relatedEvents.forEach { event ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .clickable { onEventClick(event) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = event.date, fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(text = if (isUk) event.titleUk else event.titleEn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        }
                        Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = "View", tint = Color(0xFF475569), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = label, color = Color(0xFF64748B), fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
fun StressBox(label: String, status: String, modifier: Modifier = Modifier) {
    val bgColor = when (status) {
        "HIGH" -> Color(0xFF991B1B)
        "MEDIUM" -> Color(0xFF9A3412)
        else -> Color(0xFF1E293B)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = modifier.border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label.uppercase(), fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = status, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
