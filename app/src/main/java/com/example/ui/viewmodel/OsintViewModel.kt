package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.LocalDataStore
import com.example.data.model.*
import com.example.data.repository.OsintRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OsintViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OsintRepository
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

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

    // AI Chat/Analyst State
    private val _aiAnalysisResult = MutableStateFlow<AiState>(AiState.Idle)
    val aiAnalysisResult: StateFlow<AiState> = _aiAnalysisResult.asStateFlow()

    val aiHistory = MutableStateFlow<List<AiChatMessage>>(emptyList())

    init {
        val database = AppDatabase.getDatabase(application)
        val dataStore = LocalDataStore(application)
        repository = OsintRepository(database, dataStore)

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

        // Set initial selection
        viewModelScope.launch {
            allEvents.collect {
                if (it.isNotEmpty() && selectedEvent.value == null) {
                    selectedEvent.value = it.first()
                }
            }
        }

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
            val start = args[9] as String?
            val end = args[10] as String?

            events.filter { event ->
                val matchesQuery = query.isEmpty() ||
                        event.titleEn.contains(query, ignoreCase = true) ||
                        event.titleUk.contains(query, ignoreCase = true) ||
                        event.summaryEn.contains(query, ignoreCase = true) ||
                        event.summaryUk.contains(query, ignoreCase = true) ||
                        event.impactTags.contains(query, ignoreCase = true)

                val matchesCategory = category == null || event.category == category
                val matchesTheater = theater == null || event.theater == theater
                val matchesScope = scope == null || event.eventScope == scope
                val matchesSeverity = severity == null || event.severity == severity
                val matchesVerStatus = verStatus == null || event.verificationStatus == verStatus
                val matchesRegion = region == null || event.regionId == region
                val matchesMaritime = maritime == null || event.maritimeAreaId == maritime

                val matchesDate = when {
                    start != null && end != null -> event.date >= start && event.date <= end
                    start != null -> event.date >= start
                    end != null -> event.date <= end
                    else -> true
                }

                matchesQuery && matchesCategory && matchesTheater && matchesScope &&
                        matchesSeverity && matchesVerStatus && matchesRegion && matchesMaritime && matchesDate
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
        viewModelScope.launch {
            repository.clearCache()
        }
    }

    // --- GEMINI HIGH THINKING API INTEGRATION ---

    fun askAiAnalyst(question: String) {
        if (question.trim().isEmpty()) return

        val userMessage = AiChatMessage(role = "user", content = question)
        aiHistory.value = aiHistory.value + userMessage
        _aiAnalysisResult.value = AiState.Thinking

        viewModelScope.launch {
            try {
                // Build a robust context block out of our DB records to answer the question objectively
                val contextBuilder = StringBuilder()
                contextBuilder.append("Here is the latest verified OSINT context from the local database:\n\n")

                contextBuilder.append("VERIFIED REGIONAL EVENTS:\n")
                allEvents.value.forEach { event ->
                    contextBuilder.append("- [${event.date}] ${event.titleEn} (Category: ${event.category}, Severity: ${event.severity}, Location: ${event.approximateLocationLabelEn}, Verification: ${event.verificationStatus})\n")
                    contextBuilder.append("  Summary: ${event.summaryEn}\n")
                }

                contextBuilder.append("\nREGIONAL STRESS STATUS INDICATORS:\n")
                allRegions.value.forEach { region ->
                    contextBuilder.append("- ${region.nameEn} (Fuel Stress: ${region.fuelStressStatus}, Fiscal Stress: ${region.fiscalStressStatus}, Event Count: ${region.eventCount})\n")
                }

                contextBuilder.append("\nACTIVE MARITIME ASSETS SECURITY PROFILE:\n")
                allMaritimeAssets.value.forEach { asset ->
                    contextBuilder.append("- ${asset.assetName} (${asset.assetType}, Status: ${asset.status}, Flag: ${asset.assetFlag}, Role: ${asset.assetRole})\n")
                }

                val contextString = contextBuilder.toString()

                val response = callGeminiApiWithThinking(question, contextString)
                val aiMessage = AiChatMessage(role = "model", content = response)
                aiHistory.value = aiHistory.value + aiMessage
                _aiAnalysisResult.value = AiState.Success(response)
            } catch (e: Exception) {
                _aiAnalysisResult.value = AiState.Error(e.message ?: "Failed to generate response")
            }
        }
    }

    private suspend fun callGeminiApiWithThinking(prompt: String, context: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key is not configured. Please add your GEMINI_API_KEY in the Secrets panel in AI Studio."
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val systemInstruction = """
            You are 'The Black Swan' AI OSINT Analyst, a world-class expert on analyzing documented geopolitical and economic impacts on Russia's military-economic system.
            You must provide highly analytical, professional, objective, and neutral documentary reports.
            Do not use sensationalist language, emotional prefixes, or breaking-news styling.
            You MUST adhere to the following strict safety guidelines:
            1. EXCLUDE all Ukrainian territories (including occupied Crimea, Donbas, Zaporizhzhia, Kherson) from active data overlays. If a maritime event happened near Crimea, treat it strictly as a MARITIME_REGIONAL event in the BLACK_SEA or AZOV_SEA, never as a land-based Ukrainian-territory event.
            2. Never reveal or discuss exact coordinates, exact military positions, exact strike locations, or any operational/tactical military details.
            3. All answers must have clean source references, be delayed, and focus entirely on strategic, regional, economic, fiscal, fuel, maritime, or sanctions enforcement parameters.
            4. Never use unconfirmed records. Maintain strict documentary posture.
            5. Do not use unconfirmed terms like 'successful', 'destroyed score', 'kill score', 'hit rating', or 'target value'. Focus on 'documented infrastructure disruption', 'refining outage', 'fiscal pressure', or 'vessel detentions'.
            6. Answer in the language the user asked in (English or Ukrainian) based on the input context.
        """.trimIndent()

        // Construct the Request Body using OkHttp.
        // We will set the model to 'gemini-3.1-pro-preview' and set 'thinkingLevel' to 'HIGH' as requested.
        val requestJson = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": "CONTEXT OVERVIEW:\n$context\n\nUSER INQUIRY:\n$prompt"
                    }
                  ]
                }
              ],
              "generationConfig": {
                "thinkingConfig": {
                  "thinkingLevel": "HIGH"
                },
                "temperature": 0.2
              },
              "systemInstruction": {
                "parts": [
                  {
                    "text": "$systemInstruction"
                  }
                ]
              }
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext "Error calling AI Analyst: HTTP ${response.code} ${response.message}"
            }

            val bodyString = response.body?.string() ?: return@withContext "Empty response from analyst."

            // Parse response safely using Moshi or manual extraction to be resilient
            try {
                // Manual extraction of text content is highly resilient to any nested JSON structural shifts
                val candidateStart = bodyString.indexOf("\"candidates\"")
                if (candidateStart != -1) {
                    val textStart = bodyString.indexOf("\"text\"", candidateStart)
                    if (textStart != -1) {
                        val firstQuote = bodyString.indexOf("\"", textStart + 6)
                        if (firstQuote != -1) {
                            val secondQuote = findClosingQuote(bodyString, firstQuote + 1)
                            if (secondQuote != -1) {
                                val text = bodyString.substring(firstQuote + 1, secondQuote)
                                // Unescape newlines and quotes
                                return@withContext text.replace("\\n", "\n").replace("\\\"", "\"")
                            }
                        }
                    }
                }
                "Response format mismatch. Please review log outputs."
            } catch (e: Exception) {
                "Analysis generation parsing error: ${e.message}"
            }
        }
    }

    private fun findClosingQuote(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            if (s[i] == '"' && s[i - 1] != '\\') {
                return i
            }
            i++
        }
        return -1
    }

    fun clearAiChat() {
        aiHistory.value = emptyList()
        _aiAnalysisResult.value = AiState.Idle
    }
}

// --- UTILITY STATES ---

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    data class Success(val lastVersion: String) : SyncState
    data class Error(val message: String) : SyncState
}

sealed interface AiState {
    object Idle : AiState
    object Thinking : AiState
    data class Success(val response: String) : AiState
    data class Error(val message: String) : AiState
}

data class AiChatMessage(
    val role: String, // user or model
    val content: String
)
