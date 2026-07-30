package com.gysignalstudio.blackswan.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gysignalstudio.blackswan.data.local.AppDatabase
import com.gysignalstudio.blackswan.data.local.LocalDataStore
import com.gysignalstudio.blackswan.data.model.*
import com.gysignalstudio.blackswan.data.repository.OsintRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OsintViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OsintRepository

    // Preferences and States
    val selectedLanguage: StateFlow<String>
    val themeSelection: StateFlow<String>
    val acceptedDisclaimer: StateFlow<Boolean>
    val adsDisabled: StateFlow<Boolean>

    // Raw Flows from DB
    val allEvents: StateFlow<List<EventEntity>>
    val allRegions: StateFlow<List<RegionEntity>>
    val allMaritimeAreas: StateFlow<List<MaritimeAreaEntity>>
    val allSources: StateFlow<List<SourceEntity>>
    val allSanctionsJurisdictions: StateFlow<List<SanctionsJurisdictionEntity>>
    val allMaritimeAssets: StateFlow<List<MaritimeAssetEntity>>

    // Filters State
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null) // null means All
    val selectedTheater = MutableStateFlow<String?>(null)
    val selectedScope = MutableStateFlow<String?>(null)
    val selectedSeverity = MutableStateFlow<String?>(null)
    val selectedVerificationStatus = MutableStateFlow<String?>(null)
    val selectedRegionId = MutableStateFlow<String?>(null)
    val selectedMaritimeAreaId = MutableStateFlow<String?>(null)
    val selectedYear = MutableStateFlow<String?>(null) // null means All years
    val startDateFilter = MutableStateFlow<String?>(null) // YYYY-MM-DD
    val endDateFilter = MutableStateFlow<String?>(null)

    // Filtered Events
    val filteredEvents: StateFlow<List<EventEntity>>

    // Selection States for Details
    val selectedEvent = MutableStateFlow<EventEntity?>(null)
    val selectedRegion = MutableStateFlow<RegionEntity?>(null)
    val selectedMaritimeArea = MutableStateFlow<MaritimeAreaEntity?>(null)
    val selectedSource = MutableStateFlow<SourceEntity?>(null)

    // Sync state
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        val dataStore = LocalDataStore(application)
        repository = OsintRepository(application, database, dataStore)

        viewModelScope.launch {
            repository.ensureBundledData()
            if (repository.isAutoSyncDue()) {
                syncSnapshot()
            }
        }

        // Bind preference flows
        selectedLanguage = repository.selectedLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, "en")
        themeSelection = repository.themeSelection.stateIn(viewModelScope, SharingStarted.Eagerly, "dark")
        acceptedDisclaimer = repository.acceptedDisclaimer.stateIn(viewModelScope, SharingStarted.Eagerly, false)
        adsDisabled = repository.adsDisabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

        // Bind DB flows
        allEvents = repository.allEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allRegions = repository.allRegions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allMaritimeAreas = repository.allMaritimeAreas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allSources = repository.allSources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allSanctionsJurisdictions = repository.allSanctionsJurisdictions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allMaritimeAssets = repository.allMaritimeAssets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Apply advanced multi-filtering
        filteredEvents = combine(
            allEvents,
            searchQuery,
            selectedCategory,
            selectedTheater,
            selectedScope,
            selectedSeverity,
            selectedVerificationStatus,
            selectedRegionId,
            selectedMaritimeAreaId,
            selectedYear,
            startDateFilter,
            endDateFilter
        ) { args ->
            val events = args[0] as List<EventEntity>
            val query = args[1] as String
            val category = args[2] as String?
            val theater = args[3] as String?
            val scope = args[4] as String?
            val severity = args[5] as String?
            val verStatus = args[6] as String?
            val region = args[7] as String?
            val maritime = args[8] as String?
            val year = args[9] as String?
            val start = args[10] as String?
            val end = args[11] as String?

            events.filter { event ->
                val matchesQuery = query.isEmpty() ||
                        event.titleEn.contains(query, ignoreCase = true) ||
                        event.titleUk.contains(query, ignoreCase = true) ||
                        event.summaryEn.contains(query, ignoreCase = true) ||
                        event.summaryUk.contains(query, ignoreCase = true) ||
                        event.impactTags.contains(query, ignoreCase = true)

                val matchesCategory = category == null ||
                        CHIP_CATEGORY_GROUPS[category]?.contains(event.category)
                        ?: (event.category == category)
                val matchesTheater = theater == null || event.theater == theater
                val matchesScope = scope == null || event.eventScope == scope
                val matchesSeverity = severity == null || event.severity == severity
                val matchesVerStatus = verStatus == null || event.verificationStatus == verStatus
                val matchesRegion = region == null || event.regionId == region
                val matchesMaritime = maritime == null || event.maritimeAreaId == maritime
                val matchesYear = year == null || event.date.startsWith("$year-")

                val matchesDate = when {
                    start != null && end != null -> event.date >= start && event.date <= end
                    start != null -> event.date >= start
                    end != null -> event.date <= end
                    else -> true
                }

                matchesQuery && matchesCategory && matchesTheater && matchesScope &&
                        matchesSeverity && matchesVerStatus && matchesRegion && matchesMaritime && matchesYear && matchesDate
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // Actions
    fun setLanguage(lang: String) = viewModelScope.launch { repository.setSelectedLanguage(lang) }
    fun setTheme(theme: String) = viewModelScope.launch { repository.setThemeSelection(theme) }
    fun acceptDisclaimer() = viewModelScope.launch { repository.setAcceptedDisclaimer(true) }
    fun toggleAdFree(disabled: Boolean) = viewModelScope.launch { repository.setAdsDisabled(disabled) }

    fun selectEvent(event: EventEntity) {
        selectedEvent.value = event
    }

    fun clearSelectedEvent() {
        selectedEvent.value = null
    }

    fun clearSelectedRegion() {
        selectedRegion.value = null
    }

    fun clearSelectedMaritimeArea() {
        selectedMaritimeArea.value = null
    }

    fun selectRegion(region: RegionEntity) {
        selectedRegion.value = region
    }

    fun selectMaritimeArea(area: MaritimeAreaEntity) {
        selectedMaritimeArea.value = area
    }

    fun selectSource(source: SourceEntity) {
        selectedSource.value = source
    }

    fun resetFilters() {
        searchQuery.value = ""
        selectedCategory.value = null
        selectedTheater.value = null
        selectedScope.value = null
        selectedSeverity.value = null
        selectedVerificationStatus.value = null
        selectedRegionId.value = null
        selectedMaritimeAreaId.value = null
        selectedYear.value = null
        startDateFilter.value = null
        endDateFilter.value = null
    }

    fun syncSnapshot() {
        _syncState.value = SyncState.Syncing
        viewModelScope.launch {
            val result = repository.syncDailySnapshot()
            if (result.isSuccess) {
                _syncState.value = SyncState.Success(result.getOrDefault("2026-07-09"))
            } else {
                _syncState.value = SyncState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun clearCacheAndReset() {
        _syncState.value = SyncState.Syncing
        viewModelScope.launch {
            repository.clearCache()
            val result = repository.syncDailySnapshot()
            _syncState.value = if (result.isSuccess) {
                SyncState.Success(result.getOrDefault(""))
            } else {
                SyncState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

}

// The pipeline canonicalizes every event to one of the six chip categories
// (see tools/category_taxonomy.py), so fresh data matches a chip 1:1. These
// groups only exist as a safety net for legacy data still cached on a device
// that has not re-synced yet: they map the old fine-grained categories to the
// correct chip at category level (the keyword nuance, e.g. an oil refinery
// tagged INDUSTRIAL, is resolved server-side and cannot be redone here).
private val CHIP_CATEGORY_GROUPS = mapOf(
    "FUEL_SUPPLY_DISRUPTION" to setOf(
        "FUEL_SUPPLY_DISRUPTION",
        "ENERGY_EXPORT_DISRUPTION"
    ),
    "MARITIME_ASSET_DISRUPTION" to setOf(
        "MARITIME_ASSET_DISRUPTION",
        "NAVAL_VESSEL_DAMAGE",
        "NAVAL_VESSEL_LOSS"
    ),
    "MILITARY_ASSET_DISRUPTION" to setOf(
        "MILITARY_ASSET_DISRUPTION",
        "MILITARY_INFRASTRUCTURE_DISRUPTION",
        "MILITARY_OR_INFRASTRUCTURE_STRIKE_UNCLEAR",
        "AIRFIELD_DISRUPTION",
        "AIRFIELD_OR_MILITARY_INFRASTRUCTURE_DISRUPTION",
        "AIRFIELD_AMMUNITION_STORAGE_DISRUPTION",
        "AIRFIELD_SUPPORT_INFRASTRUCTURE_DISRUPTION",
        "AMMUNITION_DEPOT_DISRUPTION",
        "GRAU_ARSENAL_DISRUPTION",
        "STRATEGIC_AVIATION_BASE_DISRUPTION",
        "STRATEGIC_AVIATION_ASSET_DAMAGE"
    ),
    "INFRASTRUCTURE_DISRUPTION" to setOf(
        "INFRASTRUCTURE_DISRUPTION",
        "PORT_LOGISTICS_DISRUPTION",
        "LOGISTICS_PRESSURE"
    ),
    "INDUSTRIAL_DISRUPTION" to setOf(
        "INDUSTRIAL_DISRUPTION",
        "DEFENSE_INDUSTRIAL_DISRUPTION"
    ),
    "SHADOW_FLEET_DISRUPTION" to setOf(
        "SHADOW_FLEET_DISRUPTION",
        "SHADOW_FLEET_SANCTIONS",
        "VESSEL_SEIZURE_OR_DETENTION",
        "VESSEL_DEREGISTRATION"
    )
)

// --- UTILITY STATES ---

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    data class Success(val lastVersion: String) : SyncState
    data class Error(val message: String) : SyncState
}


