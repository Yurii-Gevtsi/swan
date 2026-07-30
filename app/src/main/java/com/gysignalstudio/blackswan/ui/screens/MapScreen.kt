package com.gysignalstudio.blackswan.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.app.Activity
import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.gysignalstudio.blackswan.data.local.AssetDataLoader
import com.gysignalstudio.blackswan.data.local.MapEventGroupDefinition
import com.gysignalstudio.blackswan.data.local.MapEventGroupsSnapshot
import com.gysignalstudio.blackswan.data.model.EventEntity
import com.gysignalstudio.blackswan.data.model.RegionEntity
import com.gysignalstudio.blackswan.data.model.MaritimeAreaEntity
import com.gysignalstudio.blackswan.data.model.RegionAttackTotal
import com.gysignalstudio.blackswan.data.model.RegionAttackTotalsSnapshot
import com.gysignalstudio.blackswan.data.model.FuelRestrictionRegion
import com.gysignalstudio.blackswan.data.model.FuelRestrictionsSnapshot
import com.gysignalstudio.blackswan.data.model.RegionalBudgetStress
import com.gysignalstudio.blackswan.data.model.RegionalBudgetStressSnapshot
import com.gysignalstudio.blackswan.ads.AdMobBanner
import com.gysignalstudio.blackswan.ads.AdMobManager
import com.gysignalstudio.blackswan.R
import com.gysignalstudio.blackswan.ui.viewmodel.OsintViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.coroutines.resume

private const val MAP_EVENT_CHUNK_SIZE = 75
private const val SHOW_SCREENSHOT_ADS = true

private enum class RegionalLayer { NONE, FUEL_RESTRICTIONS, BUDGET_STRESS }

private data class MapMarkerGroup(
    val id: String,
    val representative: EventEntity,
    val occurrences: List<EventEntity>,
    val countOverride: Int? = null,
    val isCountAggregate: Boolean = false
) {
    val hitCount: Int get() = countOverride ?: occurrences.size
}

private fun eventTitleScore(event: EventEntity): Int =
    event.titleUk.length * 2 + event.titleEn.length + event.sources.count { it == ',' }

private fun buildMapMarkerGroups(
    events: List<EventEntity>,
    definitions: List<MapEventGroupDefinition>
): List<MapMarkerGroup> {
    val eventsById = events.associateBy(EventEntity::id)
    val consumedIds = mutableSetOf<String>()
    val groups = definitions.mapNotNull { definition ->
        val visibleEvents = definition.eventIds.mapNotNull(eventsById::get)
        if (visibleEvents.isEmpty()) return@mapNotNull null
        consumedIds += visibleEvents.map(EventEntity::id)
        val isCountAggregate = definition.aggregationType == "UNNAMED_SHADOW_FLEET_COUNT"
        val representativeSource = visibleEvents.maxBy(::eventTitleScore)
        val representative = if (isCountAggregate) {
            representativeSource.copy(
                titleUk = definition.titleUk,
                titleEn = definition.titleEn,
                approximateLocationLabelUk = definition.titleUk.substringAfter("вЂ”").trim(),
                approximateLocationLabelEn = definition.titleEn.substringAfter("вЂ”").trim(),
                lat = definition.lat,
                lng = definition.lng
            )
        } else {
            representativeSource.copy(
                titleUk = definition.titleUk,
                titleEn = definition.titleEn,
                lat = definition.lat,
                lng = definition.lng
            )
        }
        val occurrences = if (isCountAggregate) {
            listOf(representative)
        } else {
            visibleEvents
                .groupBy(EventEntity::date)
                .values
                .map { sameDay -> sameDay.maxBy(::eventTitleScore) }
                .sortedByDescending(EventEntity::date)
        }
        MapMarkerGroup(
            id = definition.id,
            representative = representative,
            occurrences = occurrences,
            countOverride = if (isCountAggregate) visibleEvents.size else null,
            isCountAggregate = isCountAggregate
        )
    }.toMutableList()

    events.asSequence()
        .filterNot { it.id in consumedIds }
        .forEach { event ->
            groups += MapMarkerGroup(
                id = "map_group_${event.id}",
                representative = event,
                occurrences = listOf(event)
            )
        }
    return groups
}

private fun String.escapeForSingleQuotedJavaScript(): String =
    replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")

private suspend fun WebView.evaluateJavaScriptAndWait(script: String) {
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}

private fun formatIndicatorNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)

private val brokenEncodingMarkers = listOf(
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

private fun String.hasBrokenEncoding(): Boolean =
    brokenEncodingMarkers.any(::contains)

private fun localizedText(isUk: Boolean, uk: String, en: String): String =
    if (isUk && uk.isNotBlank() && !uk.hasBrokenEncoding()) uk else en

@Composable
private fun IndicatorRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RegionalIndicatorCard(
    title: String,
    metric: String,
    metricLabel: String,
    accent: Color,
    adsDisabled: Boolean,
    adsReady: Boolean,
    sourceUrl: String,
    sourceLabel: String,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (adsDisabled || !adsReady) 16.dp else 104.dp)
            .border(1.dp, accent, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).heightIn(max = 390.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(metricLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Text(metric, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            content()
            Text(
                text = sourceLabel,
                color = Color(0xFF60A5FA),
                fontSize = 11.sp,
                modifier = Modifier.clickable { uriHandler.openUri(sourceUrl) }.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun DismissibleBottomPanel(
    dismissKey: Any,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val panelOffsetX = remember(dismissKey) { androidx.compose.animation.core.Animatable(0f) }
    val dismissScope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = panelOffsetX.value
                alpha = 1f - (kotlin.math.abs(panelOffsetX.value) / size.width.coerceAtLeast(1f))
            }
            .pointerInput(dismissKey) {
                val dismissThreshold = size.width * 0.3f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(panelOffsetX.value) > dismissThreshold) {
                            val target = if (panelOffsetX.value > 0) size.width.toFloat() else -size.width.toFloat()
                            dismissScope.launch {
                                panelOffsetX.animateTo(target, androidx.compose.animation.core.tween(150))
                                onDismiss()
                            }
                        } else {
                            dismissScope.launch { panelOffsetX.animateTo(0f) }
                        }
                    },
                    onDragCancel = {
                        dismissScope.launch { panelOffsetX.animateTo(0f) }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dismissScope.launch { panelOffsetX.snapTo(panelOffsetX.value + dragAmount) }
                }
            }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: OsintViewModel,
    onNavigateToEventDetails: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"

    val events by viewModel.filteredEvents.collectAsState()
    val regions by viewModel.allRegions.collectAsState()
    val maritimeAreas by viewModel.allMaritimeAreas.collectAsState()

    val adsDisabled by viewModel.adsDisabled.collectAsState()
    val adsReady by AdMobManager.adsReady.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showAttackTotals by remember { mutableStateOf(false) }
    var regionalLayer by remember { mutableStateOf(RegionalLayer.NONE) }
    var selectedAttackTotalRegionId by remember { mutableStateOf<String?>(null) }
    var selectedFuelRegionId by remember { mutableStateOf<String?>(null) }
    var selectedBudgetRegionId by remember { mutableStateOf<String?>(null) }
    var attackTotalsSnapshot by remember { mutableStateOf<RegionAttackTotalsSnapshot?>(null) }
    var fuelRestrictionsSnapshot by remember { mutableStateOf<FuelRestrictionsSnapshot?>(null) }
    var regionalBudgetStressSnapshot by remember { mutableStateOf<RegionalBudgetStressSnapshot?>(null) }
    var mapEventGroupsSnapshot by remember { mutableStateOf<MapEventGroupsSnapshot?>(null) }
    var selectedMapGroupId by remember { mutableStateOf<String?>(null) }
    val activeYear by viewModel.selectedYear.collectAsState()
    val yearScopedAttackTotals = remember(attackTotalsSnapshot, activeYear) {
        val regions = attackTotalsSnapshot?.regions.orEmpty()
        val year = activeYear
        if (year == null) {
            regions.map { region ->
                region.copy(dates = region.dates.sortedByDescending { it.date })
            }
        } else {
            regions.mapNotNull { region ->
                val dates = region.dates
                    .filter { it.date.startsWith("$year-") }
                    .sortedByDescending { it.date }
                if (dates.isEmpty()) {
                    null
                } else {
                    region.copy(
                        attackCount = dates.size,
                        targetCount = dates.sumOf { it.targetCount },
                        dates = dates
                    )
                }
            }
        }
    }
    val yearAllowsCurrentRegionalReports = activeYear == null || activeYear == "2026"
    val yearScopedFuelRestrictions = if (yearAllowsCurrentRegionalReports) fuelRestrictionsSnapshot?.regions.orEmpty() else emptyList()
    val yearScopedBudgetStress = if (yearAllowsCurrentRegionalReports) regionalBudgetStressSnapshot?.regions.orEmpty() else emptyList()
    val selectedAttackTotal = yearScopedAttackTotals.find {
        it.regionId == selectedAttackTotalRegionId
    }
    val selectedFuelRestriction = yearScopedFuelRestrictions.find { it.regionId == selectedFuelRegionId }
    val selectedBudgetStress = yearScopedBudgetStress.find { it.regionId == selectedBudgetRegionId }
    val context = LocalContext.current
    val activity = context as? Activity
    val uriHandler = LocalUriHandler.current
    fun recordFilterChangeForAds() {
        if (!adsDisabled) {
            activity?.let(AdMobManager::recordFilterChange)
        }
    }
    fun openEventDetails(event: EventEntity) {
        viewModel.selectEvent(event)
        if (!adsDisabled) {
            activity?.let(AdMobManager::recordDetailView)
        }
        onNavigateToEventDetails()
    }

    LaunchedEffect(Unit) {
        val snapshots = withContext(Dispatchers.IO) {
            arrayOf(
                AssetDataLoader.loadRegionAttackTotals(context),
                AssetDataLoader.loadFuelRestrictions(context),
                AssetDataLoader.loadRegionalBudgetStress(context),
                AssetDataLoader.loadMapEventGroups(context)
            )
        }
        attackTotalsSnapshot = snapshots[0] as RegionAttackTotalsSnapshot?
        fuelRestrictionsSnapshot = snapshots[1] as FuelRestrictionsSnapshot?
        regionalBudgetStressSnapshot = snapshots[2] as RegionalBudgetStressSnapshot?
        mapEventGroupsSnapshot = snapshots[3] as MapEventGroupsSnapshot?
    }

    val mapMarkerGroups = remember(events, mapEventGroupsSnapshot) {
        buildMapMarkerGroups(events, mapEventGroupsSnapshot?.groups.orEmpty())
    }
    val selectedMapGroup = mapMarkerGroups.find { it.id == selectedMapGroupId }

    LaunchedEffect(mapMarkerGroups, selectedMapGroupId) {
        if (selectedMapGroupId != null && selectedMapGroup == null) {
            selectedMapGroupId = null
            viewModel.clearSelectedEvent()
        }
    }

    // Map zoom action state
    var zoomTrigger by remember { mutableStateOf(0) }
    var zoomAction by remember { mutableStateOf<String?>(null) }

    // Dialog state for filters & legend
    var showFilterSheet by remember { mutableStateOf(false) }
    var showLegendDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "All" to (if (isUk) "Усі" else "All"),
        "FUEL_SUPPLY_DISRUPTION" to (if (isUk) "Нафта" else "Oil"),
        "MILITARY_ASSET_DISRUPTION" to (if (isUk) "Військові цілі" else "Military targets"),
        "SHADOW_FLEET_DISRUPTION" to (if (isUk) "Тіньовий флот" else "Shadow fleet"),
        "MARITIME_ASSET_DISRUPTION" to (if (isUk) "Морський флот" else "Naval fleet"),
        "INDUSTRIAL_DISRUPTION" to (if (isUk) "Промисловість" else "Industrial"),
        "INFRASTRUCTURE_DISRUPTION" to (if (isUk) "Інфраструктура" else "Infrastructure")
    )
    val yearOptions = listOf(
        null to (if (isUk) "\u0423\u0441\u0456" else "All"),
        "2026" to "26",
        "2025" to "25",
        "2024" to "24",
        "2023" to "23",
        "2022" to "22"
    )
    val activeYearLabel = yearOptions.firstOrNull { it.first == activeYear }?.second ?: activeYear.orEmpty()

    val activeCategory by viewModel.selectedCategory.collectAsState()

    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val selectedMaritimeArea by viewModel.selectedMaritimeArea.collectAsState()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Custom premium dark canvas background
    ) {
        // Leaflet.js and OpenStreetMap real map view
        LeafletMapView(
            eventGroups = mapMarkerGroups,
            regions = regions,
            maritimeAreas = maritimeAreas,
            regionAttackTotals = yearScopedAttackTotals,
            fuelRestrictions = yearScopedFuelRestrictions,
            regionalBudgetStress = yearScopedBudgetStress,
            showAttackTotals = showAttackTotals,
            regionalLayer = regionalLayer.name,
            selectedEventGroupId = selectedMapGroupId,
            selectedRegionId = selectedRegion?.regionId,
            selectedMaritimeId = selectedMaritimeArea?.maritimeAreaId,
            isUk = isUk,
            isDarkTheme = isDarkTheme,
            onEventClick = { groupId ->
                val group = mapMarkerGroups.find { it.id == groupId }
                if (group != null) {
                    viewModel.clearSelectedRegion()
                    viewModel.clearSelectedMaritimeArea()
                    selectedAttackTotalRegionId = null
                    selectedFuelRegionId = null
                    selectedBudgetRegionId = null
                    selectedMapGroupId = group.id
                    viewModel.selectEvent(group.representative)
                }
            },
            onRegionClick = { regionId ->
                val rg = regions.find { it.regionId == regionId }
                if (rg != null) {
                    selectedMapGroupId = null
                    viewModel.clearSelectedEvent()
                    viewModel.clearSelectedMaritimeArea()
                    selectedAttackTotalRegionId = null
                    selectedFuelRegionId = null
                    selectedBudgetRegionId = null
                    viewModel.selectRegion(rg)
                }
            },
            onMaritimeAreaClick = { areaId ->
                val ar = maritimeAreas.find { it.maritimeAreaId == areaId }
                if (ar != null) {
                    selectedMapGroupId = null
                    viewModel.clearSelectedEvent()
                    viewModel.clearSelectedRegion()
                    selectedAttackTotalRegionId = null
                    selectedFuelRegionId = null
                    selectedBudgetRegionId = null
                    viewModel.selectMaritimeArea(ar)
                }
            },
            onAggregateRegionClick = { regionId ->
                when (regionalLayer) {
                    RegionalLayer.FUEL_RESTRICTIONS -> {
                        selectedFuelRegionId = if (selectedFuelRegionId == regionId) null else regionId
                        selectedBudgetRegionId = null
                        selectedAttackTotalRegionId = null
                    }
                    RegionalLayer.BUDGET_STRESS -> {
                        selectedBudgetRegionId = if (selectedBudgetRegionId == regionId) null else regionId
                        selectedFuelRegionId = null
                        selectedAttackTotalRegionId = null
                    }
                    RegionalLayer.NONE -> {
                        selectedAttackTotalRegionId = if (selectedAttackTotalRegionId == regionId) null else regionId
                        selectedFuelRegionId = null
                        selectedBudgetRegionId = null
                    }
                }
                selectedMapGroupId = null
                viewModel.clearSelectedEvent()
                viewModel.clearSelectedRegion()
                viewModel.clearSelectedMaritimeArea()
            },
            onMapClick = {
                selectedMapGroupId = null
                viewModel.clearSelectedEvent()
                viewModel.clearSelectedRegion()
                viewModel.clearSelectedMaritimeArea()
                selectedAttackTotalRegionId = null
                selectedFuelRegionId = null
                selectedBudgetRegionId = null
            },
            zoomTrigger = zoomTrigger,
            zoomAction = zoomAction,
            onZoomActionConsumed = { zoomAction = null },
            modifier = Modifier.fillMaxSize()
        )

        // Top filter chips (page 9)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .statusBarsPadding()
                .padding(top = 2.dp)
        ) {
            var yearMenuExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "BLACK SWAN",
                    color = if (isDarkTheme) Color.White else Color(0xFF020617),
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "WAR IMPACT MAP",
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }

            // Marker name search (between title and filter chips)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                placeholder = {
                    Text(
                        text = if (isUk) "Пошук за назвою" else "Search by name",
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isUk) "Очистити" else "Clear",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Category Chips List (page 9)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .clickable { yearMenuExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${if (isUk) "\u0420\u0456\u043a" else "Year"}: $activeYearLabel",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = yearMenuExpanded,
                            onDismissRequest = { yearMenuExpanded = false }
                        ) {
                            yearOptions.forEach { (year, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (year == null) {
                                                if (isUk) "\u0423\u0441\u0456 \u0440\u043e\u043a\u0438" else "All years"
                                            } else {
                                                "20$label"
                                            }
                                        )
                                    },
                                    onClick = {
                                        val previousYear = activeYear
                                        viewModel.selectedYear.value = year
                                        selectedAttackTotalRegionId = null
                                        selectedFuelRegionId = null
                                        selectedBudgetRegionId = null
                                        selectedMapGroupId = null
                                        viewModel.clearSelectedEvent()
                                        if (year != null && year != "2026" && regionalLayer != RegionalLayer.NONE) {
                                            regionalLayer = RegionalLayer.NONE
                                        }
                                        yearMenuExpanded = false
                                        if (previousYear != year) {
                                            recordFilterChangeForAds()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    val isSelected = showAttackTotals
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) Color(0xFFF59E0B) else MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(20.dp))
                            .clickable {
                                showAttackTotals = !showAttackTotals
                                regionalLayer = RegionalLayer.NONE
                                selectedAttackTotalRegionId = null
                                selectedFuelRegionId = null
                                selectedBudgetRegionId = null
                                selectedMapGroupId = null
                                viewModel.clearSelectedEvent()
                                recordFilterChangeForAds()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isUk) "\u0421\u0443\u043c\u0430\u0440\u043d\u0456 \u0430\u0442\u0430\u043a\u0438" else "Attack totals",
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                item {
                    val isSelected = regionalLayer == RegionalLayer.FUEL_RESTRICTIONS
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) Color(0xFFF97316) else MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFFF97316), RoundedCornerShape(20.dp))
                            .clickable {
                                regionalLayer = if (isSelected) RegionalLayer.NONE else RegionalLayer.FUEL_RESTRICTIONS
                                showAttackTotals = false
                                selectedAttackTotalRegionId = null
                                selectedFuelRegionId = null
                                selectedBudgetRegionId = null
                                selectedMapGroupId = null
                                viewModel.clearSelectedEvent()
                                recordFilterChangeForAds()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isUk) "\u041f\u0430\u043b\u0438\u0432\u043d\u0456 \u043e\u0431\u043c\u0435\u0436\u0435\u043d\u043d\u044f" else "Fuel restrictions",
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                item {
                    val isSelected = regionalLayer == RegionalLayer.BUDGET_STRESS
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) Color(0xFFDC2626) else MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFFDC2626), RoundedCornerShape(20.dp))
                            .clickable {
                                regionalLayer = if (isSelected) RegionalLayer.NONE else RegionalLayer.BUDGET_STRESS
                                showAttackTotals = false
                                selectedAttackTotalRegionId = null
                                selectedFuelRegionId = null
                                selectedBudgetRegionId = null
                                selectedMapGroupId = null
                                viewModel.clearSelectedEvent()
                                recordFilterChangeForAds()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isUk) "\u0414\u0435\u0444\u0456\u0446\u0438\u0442 \u0431\u044e\u0434\u0436\u0435\u0442\u0443" else "Budget deficit",
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                items(categories) { (id, label) ->
                    val isSelected = !showAttackTotals && regionalLayer == RegionalLayer.NONE && ((id == "All" && activeCategory == null) || (activeCategory == id))
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .clickable {
                                val previousCategory = activeCategory
                                val previousShowAttackTotals = showAttackTotals
                                val previousRegionalLayer = regionalLayer
                                val nextCategory = if (id == "All") null else id
                                showAttackTotals = false
                                regionalLayer = RegionalLayer.NONE
                                selectedAttackTotalRegionId = null
                                selectedFuelRegionId = null
                                selectedBudgetRegionId = null
                                if (id == "All") {
                                    viewModel.selectedCategory.value = null
                                } else {
                                    viewModel.selectedCategory.value = id
                                }
                                if (
                                    previousCategory != nextCategory ||
                                    previousShowAttackTotals ||
                                    previousRegionalLayer != RegionalLayer.NONE
                                ) {
                                    recordFilterChangeForAds()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (
            selectedMapGroup != null ||
            selectedAttackTotal != null ||
            selectedFuelRestriction != null ||
            selectedBudgetStress != null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        selectedMapGroupId = null
                        viewModel.clearSelectedEvent()
                        selectedAttackTotalRegionId = null
                        selectedFuelRegionId = null
                        selectedBudgetRegionId = null
                    }
            )
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            Spacer(modifier = Modifier.height(8.dp))

            FloatingActionButton(
                onClick = {
                    viewModel.clearSelectedEvent()
                    selectedAttackTotalRegionId = null
                    selectedFuelRegionId = null
                    selectedBudgetRegionId = null
                    zoomAction = "RESET"
                    zoomTrigger++
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset View")
            }

            Spacer(modifier = Modifier.height(8.dp))

            FloatingActionButton(
                onClick = { showLegendDialog = true },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Legend")
            }
        }

        selectedAttackTotal?.let { aggregate ->
            DismissibleBottomPanel(
                dismissKey = aggregate.regionId,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = if (adsDisabled || !adsReady) (-16).dp else (-104).dp),
                onDismiss = {
                    selectedAttackTotalRegionId = null
                }
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isUk) aggregate.regionNameUk else aggregate.regionNameEn,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isUk) "\u0430\u0442\u0430\u043a \u0437\u0430 \u0443\u043d\u0456\u043a\u0430\u043b\u044c\u043d\u0438\u043c\u0438 \u0434\u0430\u0442\u0430\u043c\u0438" else "attack days",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = aggregate.attackCount.toString(),
                                color = Color(0xFFFBBF24),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isUk) "\u0426\u0456\u043b\u0435\u0439: ${aggregate.targetCount}" else "Targets: ${aggregate.targetCount}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            text = if (isUk) attackTotalsSnapshot?.metricDescriptionUk.orEmpty() else attackTotalsSnapshot?.metricDescriptionEn.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        aggregate.dates.forEach { day ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "${day.date}  (${day.targetCount})",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = day.objects.joinToString(", "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedFuelRestriction?.let { region ->
            DismissibleBottomPanel(
                dismissKey = region.regionId,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = if (adsDisabled || !adsReady) (-16).dp else (-104).dp),
                onDismiss = {
                    selectedFuelRegionId = null
                }
            ) {
                RegionalIndicatorCard(
                    title = if (isUk) region.regionNameUk else region.regionNameEn,
                    metric = region.severity.toString(),
                    metricLabel = if (isUk) "\u0406\u043d\u0434\u0435\u043a\u0441 \u0433\u043e\u0441\u0442\u0440\u043e\u0442\u0438 (0-4)" else "Severity index (0-4)",
                    accent = Color(0xFFF97316),
                    adsDisabled = adsDisabled,
                    adsReady = adsReady,
                    sourceUrl = region.sourceUrl,
                    sourceLabel = if (isUk) "\u0414\u0436\u0435\u0440\u0435\u043b\u043e" else "Source",
                    uriHandler = uriHandler
                ) {
                    IndicatorRow(if (isUk) "\u0421\u0442\u0430\u0442\u0443\u0441" else "Status", if (isUk) region.statusUk else region.statusEn)
                    IndicatorRow(if (isUk) "\u0411\u0435\u043d\u0437\u0438\u043d, \u043b" else "Gasoline, L", region.gasolineLimitLiters?.toInt()?.toString() ?: "-")
                    IndicatorRow(if (isUk) "\u0414\u0438\u0437\u0435\u043b\u044c, \u043b" else "Diesel, L", region.dieselLimitLiters?.toInt()?.toString() ?: "-")
                    Text(
                        text = if (isUk) region.coverageUk else region.coverageEn,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = if (isUk) region.restrictionUk else region.restrictionEn,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        selectedBudgetStress?.let { region ->
            DismissibleBottomPanel(
                dismissKey = region.regionId,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = if (adsDisabled || !adsReady) (-16).dp else (-104).dp),
                onDismiss = {
                    selectedBudgetRegionId = null
                }
            ) {
                RegionalIndicatorCard(
                    title = if (isUk) region.regionNameUk else region.regionNameEn,
                    metric = "${formatIndicatorNumber(region.deficitPercentRevenue)}%",
                    metricLabel = if (isUk) "\u0414\u0435\u0444\u0456\u0446\u0438\u0442 \u0432\u0456\u0434 \u0432\u043b\u0430\u0441\u043d\u0438\u0445 \u0434\u043e\u0445\u043e\u0434\u0456\u0432" else "Deficit of own revenue",
                    accent = Color(0xFFDC2626),
                    adsDisabled = adsDisabled,
                    adsReady = adsReady,
                    sourceUrl = region.budgetSourceUrl,
                    sourceLabel = if (isUk) "\u0414\u0436\u0435\u0440\u0435\u043b\u043e \u0431\u044e\u0434\u0436\u0435\u0442\u0443" else "Budget source",
                    uriHandler = uriHandler
                ) {
                    IndicatorRow(if (isUk) "\u0411\u0430\u043b\u0430\u043d\u0441" else "Balance", if (isUk) region.statusUk else region.statusEn)
                    IndicatorRow(if (isUk) "\u0414\u0435\u0444\u0456\u0446\u0438\u0442, \u043c\u043b\u043d \u0440\u0443\u0431." else "Deficit, RUB m", region.deficitMillionRub?.let(::formatIndicatorNumber) ?: "-")
                    IndicatorRow(if (isUk) "\u0412\u043b\u0430\u0441\u043d\u0456 \u0434\u043e\u0445\u043e\u0434\u0438, \u043c\u043b\u0440\u0434 \u0440\u0443\u0431." else "Own revenue, RUB bn", region.ownRevenueBillionRub?.let(::formatIndicatorNumber) ?: "-")
                    IndicatorRow(if (isUk) "\u0414\u0435\u0440\u0436\u0431\u043e\u0440\u0433 01.06, \u043c\u043b\u0440\u0434 \u0440\u0443\u0431." else "Public debt 1 Jun, RUB bn", region.publicDebtJuneBillionRub?.let(::formatIndicatorNumber) ?: "-")
                    IndicatorRow(if (isUk) "\u0417\u043c\u0456\u043d\u0430 \u0431\u043e\u0440\u0433\u0443 \u0437 \u0441\u0456\u0447\u043d\u044f" else "Debt change since January", region.debtChangePercent?.let { "${formatIndicatorNumber(it)}%" } ?: "-")
                    Text(
                        text = if (isUk) region.descriptionUk else region.descriptionEn,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = if (isUk) "\u0414\u0436\u0435\u0440\u0435\u043b\u043e \u0431\u043e\u0440\u0433\u0443" else "Debt source",
                        color = Color(0xFF60A5FA),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { uriHandler.openUri(region.debtSourceUrl) }.padding(top = 10.dp)
                    )
                }
            }
        }

        // Selected Event Preview Bottom Panel (page 9).
        // Shown only after tapping an event; swipe left/right (or tap the map) to dismiss.
        selectedMapGroup?.let { group ->
            val event = group.representative
            val sortedOccurrences = remember(group.id, group.occurrences) {
                group.occurrences.sortedByDescending(EventEntity::date)
            }
            val sourceCount = sortedOccurrences
                .flatMap { it.sources.split(",") }
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
                .size
            DismissibleBottomPanel(
                dismissKey = group.id,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = if (adsDisabled || !adsReady) (-16).dp else (-104).dp),
                onDismiss = {
                    selectedMapGroupId = null
                    viewModel.clearSelectedEvent()
                }
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clickable(enabled = group.hitCount == 1) {
                            openEventDetails(event)
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.fire_marker),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = categoryLabel(event.category, isUk),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (group.hitCount == 1) {
                                    event.date
                                } else if (group.isCountAggregate && isUk) {
                                    "${group.hitCount} суден"
                                } else if (group.isCountAggregate) {
                                    "${group.hitCount} vessels"
                                } else if (isUk) {
                                    "${group.hitCount} уражень"
                                } else {
                                    "${group.hitCount} strikes"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = localizedText(isUk, event.titleUk, event.titleEn),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = localizedText(isUk, event.approximateLocationLabelUk, event.approximateLocationLabelEn),
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium
                        )

                        if (group.hitCount > 1 && !group.isCountAggregate) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isUk) "Історія уражень" else "Strike history",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                sortedOccurrences.forEachIndexed { index, occurrence ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                openEventDetails(occurrence)
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = occurrence.date,
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.width(82.dp)
                                        )
                                        Text(
                                            text = localizedText(isUk, occurrence.titleUk, occurrence.titleEn),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (index < sortedOccurrences.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Source,
                                contentDescription = "Sources",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$sourceCount ${if (isUk) "джерел" else "sources"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (group.hitCount == 1) {
                                    if (isUk) "Детальніше" else "Details"
                                } else if (group.isCountAggregate) {
                                    if (isUk) "Загальна кількість" else "Total count"
                                } else {
                                    if (isUk) "Усі дати" else "All dates"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        if (SHOW_SCREENSHOT_ADS && !adsDisabled && adsReady) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AdMobBanner(modifier = Modifier.fillMaxWidth())
            }
        }
        // --- FILTER PANEL BOTTOM SHEET DIALOG (page 9) ---
        if (showFilterSheet) {
            Dialog(onDismissRequest = { showFilterSheet = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
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
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { showFilterSheet = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Theater Filter
                        Text(
                            text = if (isUk) "Театр дій" else "Theater of Action",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val theaterList = listOf("RUSSIA_INTERNAL", "BLACK_SEA", "AZOV_SEA", "BALTIC_SEA")
                            theaterList.forEach { th ->
                                val active = viewModel.selectedTheater.collectAsState().value == th
                                Box(
                                    modifier = Modifier
                                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                        .clickable {
                                            viewModel.selectedTheater.value = if (active) null else th
                                            recordFilterChangeForAds()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = th.replace("_", " "),
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val severityList = listOf("LOW", "MEDIUM", "HIGH", "SYSTEMIC")
                            severityList.forEach { sev ->
                                val active = viewModel.selectedSeverity.collectAsState().value == sev
                                Box(
                                    modifier = Modifier
                                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                        .clickable {
                                            viewModel.selectedSeverity.value = if (active) null else sev
                                            recordFilterChangeForAds()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = sev,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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
                                    recordFilterChangeForAds()
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isUk) "ЛЕГЕНДА КАРТИ" else "MAP LEGEND",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { showLegendDialog = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LegendRow(color = Color(0xFFEF4444), label = if (isUk) "Збій енергетичного експорту" else "Energy export disruption")
                        LegendRow(color = Color(0xFFF97316), label = if (isUk) "\u0423\u0440\u0430\u0436\u0435\u043d\u043d\u044f \u0442\u0456\u043d\u044c\u043e\u0432\u043e\u0433\u043e \u0444\u043b\u043e\u0442\u0443" else "Shadow fleet disruption")
                        LegendRow(color = Color(0xFF10B981), label = if (isUk) "Логістичний чи портовий збій" else "Port or logistics disruption")
                        LegendRow(color = Color(0xFF38BDF8), label = if (isUk) "Морська регіональна зона" else "Maritime regional zone")

                        Spacer(modifier = Modifier.height(12.dp))

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isUk) {
                                "Усі маркери та зони відображають узагальнені й неточні координати з міркувань безпеки."
                            } else {
                                "All boundaries and zones represent generalized and inaccurate locations for security purposes."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MapEventDto(
    val id: String,
    val lat: Double,
    val lng: Double,
    val hitCount: Int,
    val category: String,
    val titleEn: String,
    val titleUk: String,
    val isSbsShadowFleetItem: Boolean,
    val isMapAggregateOnly: Boolean
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
private fun LeafletMapView(
    eventGroups: List<MapMarkerGroup>,
    regions: List<RegionEntity>,
    maritimeAreas: List<MaritimeAreaEntity>,
    regionAttackTotals: List<RegionAttackTotal>,
    fuelRestrictions: List<FuelRestrictionRegion>,
    regionalBudgetStress: List<RegionalBudgetStress>,
    showAttackTotals: Boolean,
    regionalLayer: String,
    selectedEventGroupId: String?,
    selectedRegionId: String?,
    selectedMaritimeId: String?,
    isUk: Boolean,
    isDarkTheme: Boolean,
    onEventClick: (String) -> Unit,
    onRegionClick: (String) -> Unit,
    onMaritimeAreaClick: (String) -> Unit,
    onAggregateRegionClick: (String) -> Unit,
    onMapClick: () -> Unit,
    zoomTrigger: Int,
    zoomAction: String?,
    onZoomActionConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moshi = remember { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    val eventsAdapter = remember { moshi.adapter<List<MapEventDto>>(Types.newParameterizedType(List::class.java, MapEventDto::class.java)) }
    val regionsAdapter = remember { moshi.adapter<List<MapRegionDto>>(Types.newParameterizedType(List::class.java, MapRegionDto::class.java)) }
    val maritimeAdapter = remember { moshi.adapter<List<MapMaritimeDto>>(Types.newParameterizedType(List::class.java, MapMaritimeDto::class.java)) }
    val attackTotalsAdapter = remember { moshi.adapter<List<RegionAttackTotal>>(Types.newParameterizedType(List::class.java, RegionAttackTotal::class.java)) }
    val fuelRestrictionsAdapter = remember { moshi.adapter<List<FuelRestrictionRegion>>(Types.newParameterizedType(List::class.java, FuelRestrictionRegion::class.java)) }
    val budgetStressAdapter = remember { moshi.adapter<List<RegionalBudgetStress>>(Types.newParameterizedType(List::class.java, RegionalBudgetStress::class.java)) }

    val eventsDto = remember(eventGroups) {
        eventGroups.map { group ->
            val event = group.representative
            MapEventDto(
                group.id,
                event.lat,
                event.lng,
                group.hitCount,
                event.category,
                event.titleEn,
                event.titleUk,
                event.category == "SHADOW_FLEET_DISRUPTION",
                event.id == "event_20260714_shadow_fleet_azov_operation_total_001" ||
                    event.id == "event_20260715_shadow_fleet_black_sea_group_001"
            )
        }
    }
    val regionsDto = remember(regions) {
        regions.map { MapRegionDto(it.regionId, it.lat, it.lng, it.defaultRadiusKm, it.fuelStressStatus, it.fiscalStressStatus, it.nameEn, it.nameUk) }
    }
    val maritimeDto = remember(maritimeAreas) {
        maritimeAreas.map { MapMaritimeDto(it.maritimeAreaId, it.lat, it.lng, it.defaultRadiusKm, it.theater, it.nameEn, it.nameUk) }
    }

    val eventChunksJson = remember(eventsDto) {
        eventsDto.chunked(MAP_EVENT_CHUNK_SIZE).map(eventsAdapter::toJson)
    }
    val regionsJson = remember(regionsDto) { regionsAdapter.toJson(regionsDto) }
    val maritimeJson = remember(maritimeDto) { maritimeAdapter.toJson(maritimeDto) }
    val attackTotalsJson = remember(regionAttackTotals) { attackTotalsAdapter.toJson(regionAttackTotals) }
    val fuelRestrictionsJson = remember(fuelRestrictions) { fuelRestrictionsAdapter.toJson(fuelRestrictions) }
    val budgetStressJson = remember(regionalBudgetStress) { budgetStressAdapter.toJson(regionalBudgetStress) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(eventChunksJson, regionsJson, maritimeJson, attackTotalsJson, fuelRestrictionsJson, budgetStressJson, showAttackTotals, regionalLayer, selectedEventGroupId, selectedRegionId, selectedMaritimeId, isUk, isDarkTheme, isLoaded) {
        val webView = webViewRef
        if (webView != null && isLoaded) {
            webView.setBackgroundColor(
                if (isDarkTheme) android.graphics.Color.rgb(9, 13, 22)
                else android.graphics.Color.rgb(234, 240, 247)
            )
            webView.evaluateJavaScriptAndWait("setMapTheme($isDarkTheme);")
            webView.evaluateJavaScriptAndWait("beginMapEvents();")
            if (!showAttackTotals && regionalLayer == RegionalLayer.NONE.name) {
                eventChunksJson.forEach { chunk ->
                    webView.evaluateJavaScriptAndWait(
                        "appendMapEvents('${chunk.escapeForSingleQuotedJavaScript()}');"
                    )
                }
            }

            val js = "finishMapData('${regionsJson.escapeForSingleQuotedJavaScript()}', " +
                    "'${maritimeJson.escapeForSingleQuotedJavaScript()}', " +
                    "${if (selectedEventGroupId != null) "'$selectedEventGroupId'" else "null"}, " +
                    "${if (selectedRegionId != null) "'$selectedRegionId'" else "null"}, " +
                    "${if (selectedMaritimeId != null) "'$selectedMaritimeId'" else "null"}, $isUk, " +
                    "$showAttackTotals, '${attackTotalsJson.escapeForSingleQuotedJavaScript()}', " +
                    "'$regionalLayer', '${fuelRestrictionsJson.escapeForSingleQuotedJavaScript()}', " +
                    "'${budgetStressJson.escapeForSingleQuotedJavaScript()}');"
            webView.evaluateJavaScriptAndWait(js)
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

    // Captured under different names: inside the bridge object below, a bare
    // `onEventClick(...)` would resolve to the member function itself (infinite recursion).
    // rememberUpdatedState keeps them current вЂ” the WebView factory runs only once, so plain
    // captures would forever point at the first composition's lambdas (with empty data lists).
    val eventClickHandler by rememberUpdatedState(onEventClick)
    val regionClickHandler by rememberUpdatedState(onRegionClick)
    val maritimeClickHandler by rememberUpdatedState(onMaritimeAreaClick)
    val aggregateRegionClickHandler by rememberUpdatedState(onAggregateRegionClick)
    val mapClickHandler by rememberUpdatedState(onMapClick)

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef = this
                setBackgroundColor(
                    if (isDarkTheme) android.graphics.Color.rgb(9, 13, 22)
                    else android.graphics.Color.rgb(234, 240, 247)
                )
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }

                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onEventClick(eventId: String) {
                        eventClickHandler(eventId)
                    }

                    @android.webkit.JavascriptInterface
                    fun onRegionClick(regionId: String) {
                        regionClickHandler(regionId)
                    }

                    @android.webkit.JavascriptInterface
                    fun onMaritimeAreaClick(areaId: String) {
                        maritimeClickHandler(areaId)
                    }

                    @android.webkit.JavascriptInterface
                    fun onAggregateRegionClick(regionId: String) {
                        aggregateRegionClickHandler(regionId)
                    }

                    @android.webkit.JavascriptInterface
                    fun onMapClick() {
                        mapClickHandler()
                    }
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoaded = true
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        android.util.Log.e(
                            "MapWebView",
                            "load error ${error?.errorCode} ${error?.description} for ${request?.url}"
                        )
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d(
                            "MapWebView",
                            "[${message.messageLevel()}] ${message.message()} @${message.sourceId()}:${message.lineNumber()}"
                        )
                        return true
                    }
                }
                WebView.setWebContentsDebuggingEnabled(com.gysignalstudio.blackswan.BuildConfig.DEBUG)

                // Fully offline map: Leaflet + Natural Earth basemap are bundled in assets.
                loadUrl("file:///android_asset/map.html")
            }
        },
        modifier = modifier
    )
}

// Bilingual display label for a canonical event category. Data is canonicalized
// to the six map categories in the pipeline; any legacy value falls back to a
// readable form of the raw id.
fun categoryLabel(category: String, isUk: Boolean): String = when (category) {
    "FUEL_SUPPLY_DISRUPTION", "ENERGY_EXPORT_DISRUPTION" -> if (isUk) "Нафта" else "Oil"
    "MILITARY_ASSET_DISRUPTION" -> if (isUk) "Військові цілі" else "Military targets"
    "SHADOW_FLEET_DISRUPTION" -> if (isUk) "Тіньовий флот" else "Shadow fleet"
    "MARITIME_ASSET_DISRUPTION" -> if (isUk) "Морський флот" else "Naval fleet"
    "INDUSTRIAL_DISRUPTION" -> if (isUk) "Промисловість" else "Industrial"
    "INFRASTRUCTURE_DISRUPTION" -> if (isUk) "Інфраструктура" else "Infrastructure"
    else -> category.replace("_", " ")
}
