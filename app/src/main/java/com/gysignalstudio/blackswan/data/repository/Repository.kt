package com.gysignalstudio.blackswan.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.gysignalstudio.blackswan.data.local.AppDatabase
import com.gysignalstudio.blackswan.data.local.AssetDataLoader
import com.gysignalstudio.blackswan.data.local.LocalDataStore
import com.gysignalstudio.blackswan.data.model.*
import com.gysignalstudio.blackswan.data.remote.RemoteDataSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OsintRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val dataStore: LocalDataStore
) {
    companion object {
        // Hosting data refreshes every ~2 days; checking the tiny manifest daily
        // keeps users at most one day behind while downloads happen only when
        // the dataVersion actually changes.
        private const val AUTO_SYNC_INTERVAL_MS = 24L * 60 * 60 * 1000
    }

    val allEvents: Flow<List<EventEntity>> = database.eventDao().getAllEvents()
    val allRegions: Flow<List<RegionEntity>> = database.regionDao().getAllRegions()
    val allMaritimeAreas: Flow<List<MaritimeAreaEntity>> = database.maritimeAreaDao().getAllMaritimeAreas()
    val allSources: Flow<List<SourceEntity>> = database.sourceDao().getAllSources()
    val allSanctionsJurisdictions: Flow<List<SanctionsJurisdictionEntity>> = database.sanctionsJurisdictionDao().getAllSanctionsJurisdictions()
    val allMaritimeAssets: Flow<List<MaritimeAssetEntity>> = database.maritimeAssetDao().getAllMaritimeAssets()

    // Preferences
    val selectedLanguage: Flow<String> = dataStore.selectedLanguage
    val themeSelection: Flow<String> = dataStore.themeSelection
    val acceptedDisclaimer: Flow<Boolean> = dataStore.acceptedDisclaimer
    val adsDisabled: Flow<Boolean> = dataStore.adsDisabled

    suspend fun setSelectedLanguage(language: String) = dataStore.setSelectedLanguage(language)
    suspend fun setThemeSelection(theme: String) = dataStore.setThemeSelection(theme)
    suspend fun setAcceptedDisclaimer(accepted: Boolean) = dataStore.setAcceptedDisclaimer(accepted)
    suspend fun setAdsDisabled(disabled: Boolean) = dataStore.setAdsDisabled(disabled)

    suspend fun getEventById(id: String): EventEntity? = database.eventDao().getEventById(id)
    suspend fun getSourceById(id: String): SourceEntity? = database.sourceDao().getSourceById(id)
    suspend fun getRegionById(id: String): RegionEntity? = database.regionDao().getRegionById(id)
    suspend fun getMaritimeAreaById(id: String): MaritimeAreaEntity? = database.maritimeAreaDao().getMaritimeAreaById(id)

    suspend fun ensureBundledData() = withContext(Dispatchers.IO) {
        val currentEvents = database.eventDao().getAllEvents().first()
        if (currentEvents.isEmpty()) {
            populateFromBundledData()
            return@withContext
        }
        upsertSnapshotData()
    }

    /**
     * Upserts the current snapshot files (synced if present, bundled otherwise)
     * into Room, touching only records that actually changed.
     */
    private suspend fun upsertSnapshotData() {
        val snapshotEvents = AssetDataLoader.loadEvents(context)
        if (snapshotEvents.isNotEmpty()) {
            val currentById = database.eventDao().getAllEvents().first().associateBy(EventEntity::id)
            val changedEvents = snapshotEvents.filter { event -> currentById[event.id] != event }
            if (changedEvents.isNotEmpty()) {
                database.eventDao().insertEvents(changedEvents)
                Log.i("OsintRepository", "Updated ${changedEvents.size} snapshot events in local database")
            }
        }

        val snapshotSources = AssetDataLoader.loadWikipediaCitationSources(context)
        if (snapshotSources.isNotEmpty()) {
            val currentSources = database.sourceDao().getAllSources().first().associateBy(SourceEntity::sourceId)
            val changedSources = snapshotSources.filter { source -> currentSources[source.sourceId] != source }
            if (changedSources.isNotEmpty()) {
                database.sourceDao().insertSources(changedSources)
                Log.i("OsintRepository", "Updated ${changedSources.size} snapshot citation sources in local database")
            }
        }
    }

    /**
     * Fetches the published data manifest from hosting and, when its version
     * differs from the one applied last time, downloads the snapshot files and
     * upserts them into Room. Offline-first: on any failure the app keeps
     * serving the local data untouched.
     */
    suspend fun syncDailySnapshot(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val manifest = RemoteDataSync.fetchManifest()
            val appliedVersion = dataStore.lastDataVersion.first()
            if (manifest.dataVersion != appliedVersion) {
                RemoteDataSync.downloadAndCommit(context, manifest.files)
                upsertSnapshotData()
                dataStore.setLastDataVersion(manifest.dataVersion)
                Log.i("OsintRepository", "Applied remote data version ${manifest.dataVersion}")
            }
            dataStore.setLastSuccessfulSync(System.currentTimeMillis())
            Result.success(manifest.dataVersion)
        } catch (e: Exception) {
            Log.w("OsintRepository", "Snapshot sync failed", e)
            Result.failure(e)
        }
    }

    /** True when the last successful sync is older than [AUTO_SYNC_INTERVAL_MS]. */
    suspend fun isAutoSyncDue(): Boolean {
        val lastSync = dataStore.lastSuccessfulSync.first()
        return System.currentTimeMillis() - lastSync > AUTO_SYNC_INTERVAL_MS
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        database.eventDao().clearAllEvents()
        database.regionDao().clearAllRegions()
        database.maritimeAreaDao().clearAllMaritimeAreas()
        database.sourceDao().clearAllSources()
        database.maritimeAssetDao().clearAllMaritimeAssets()
        database.sanctionsJurisdictionDao().clearAllSanctionsJurisdictions()

        populateFromBundledData()
    }

    private suspend fun populateFromBundledData() {
        val importedEvents = AssetDataLoader.loadEvents(context)
        val seedEvents = importedEvents.ifEmpty { SampleOsintData.events }
        val seedSources = (SampleOsintData.sources + AssetDataLoader.loadWikipediaCitationSources(context))
            .associateBy(SourceEntity::sourceId)
            .values
            .toList()
        database.withTransaction {
            database.sourceDao().insertSources(seedSources)
            database.regionDao().insertRegions(SampleOsintData.regions)
            database.maritimeAreaDao().insertMaritimeAreas(SampleOsintData.maritimeAreas)
            database.sanctionsJurisdictionDao().insertSanctionsJurisdictions(SampleOsintData.sanctionsJurisdictions)
            database.maritimeAssetDao().insertMaritimeAssets(SampleOsintData.maritimeAssets)
            database.eventDao().insertEvents(seedEvents)
        }
        Log.i("OsintRepository", "Bundled database ready with ${seedEvents.size} events")
    }
}
