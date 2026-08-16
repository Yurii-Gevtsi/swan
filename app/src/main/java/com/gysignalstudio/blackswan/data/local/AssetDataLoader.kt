package com.gysignalstudio.blackswan.data.local

import android.content.Context
import android.util.Log
import java.io.File
import com.gysignalstudio.blackswan.data.model.EventEntity
import com.gysignalstudio.blackswan.data.model.RegionAttackTotalsSnapshot
import com.gysignalstudio.blackswan.data.model.FuelRestrictionsSnapshot
import com.gysignalstudio.blackswan.data.model.RegionalBudgetStressSnapshot
import com.gysignalstudio.blackswan.data.model.SpecialOperationsSnapshot
import com.gysignalstudio.blackswan.data.model.LossTotalsSnapshot
import com.gysignalstudio.blackswan.data.model.SourceEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class EventImportSnapshot(
    val schemaVersion: Int,
    val recordCount: Int,
    val events: List<EventEntity>
)

@JsonClass(generateAdapter = true)
data class SourceImportSnapshot(
    val schemaVersion: Int,
    val recordCount: Int,
    val sources: List<SourceEntity>
)

@JsonClass(generateAdapter = true)
data class MapEventGroupDefinition(
    val id: String,
    val representativeEventId: String,
    val eventIds: List<String>,
    val occurrenceEventIds: List<String>,
    val hitCount: Int,
    val aliasDuplicateCount: Int,
    val lat: Double,
    val lng: Double,
    val titleUk: String,
    val titleEn: String,
    val aggregationType: String? = null
)

@JsonClass(generateAdapter = true)
data class MapEventGroupsSnapshot(
    val schemaVersion: Int,
    val eventCount: Int,
    val groupCount: Int,
    val groups: List<MapEventGroupDefinition>
)

object AssetDataLoader {
    private const val TAG = "AssetDataLoader"
    private const val SYNCED_DATA_DIR = "synced_data"
    const val EVENTS_ASSET = "osint_events.json"
    private const val MAP_EVENT_GROUPS_ASSET = "map_event_groups.json"
    private const val REGION_ATTACK_TOTALS_ASSET = "region_attack_totals_2026.json"
    private const val FUEL_RESTRICTIONS_ASSET = "fuel_shortage_regions_2026.json"
    private const val REGIONAL_BUDGET_STRESS_ASSET = "regional_budget_stress_2026.json"
    private const val WIKIPEDIA_CITATION_SOURCES_ASSET = "wikipedia_citation_sources.json"
    private const val SPECIAL_OPERATIONS_ASSET = "special_events_2022_2025.json"
    private const val LOSS_TOTALS_ASSET = "loss_totals.json"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    /** Directory holding data files downloaded from hosting; they override bundled assets. */
    fun syncedDataDir(context: Context): File = File(context.filesDir, SYNCED_DATA_DIR)

    private fun readJson(context: Context, name: String): String {
        val syncedFile = File(syncedDataDir(context), name)
        if (syncedFile.isFile && syncedFile.length() > 0) {
            try {
                return syncedFile.readText()
            } catch (error: Exception) {
                Log.w(TAG, "Failed to read synced $name, falling back to bundled asset", error)
            }
        }
        return context.assets.open(name).bufferedReader().use { it.readText() }
    }

    fun parseEvents(json: String): List<EventEntity> {
        val snapshot = moshi.adapter(EventImportSnapshot::class.java).fromJson(json)
        return snapshot?.events.orEmpty()
    }

    fun loadEvents(context: Context): List<EventEntity> {
        return try {
            val json = readJson(context, EVENTS_ASSET)
            val adapter = moshi.adapter(EventImportSnapshot::class.java)
            val snapshot = adapter.fromJson(json)
            val events = snapshot?.events.orEmpty()
            if (snapshot != null && snapshot.recordCount != events.size) {
                Log.w(TAG, "Asset recordCount=${snapshot.recordCount}, parsed=${events.size}")
            }
            Log.i(TAG, "Loaded ${events.size} events from $EVENTS_ASSET")
            events
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load $EVENTS_ASSET", error)
            emptyList()
        }
    }

    fun loadWikipediaCitationSources(context: Context): List<SourceEntity> {
        return try {
            val json = readJson(context, WIKIPEDIA_CITATION_SOURCES_ASSET)
            val adapter = moshi.adapter(SourceImportSnapshot::class.java)
            val snapshot = adapter.fromJson(json)
            val sources = snapshot?.sources.orEmpty()
            if (snapshot != null && snapshot.recordCount != sources.size) {
                Log.w(TAG, "Citation source recordCount=${snapshot.recordCount}, parsed=${sources.size}")
            }
            Log.i(TAG, "Loaded ${sources.size} citation sources from $WIKIPEDIA_CITATION_SOURCES_ASSET")
            sources
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load $WIKIPEDIA_CITATION_SOURCES_ASSET", error)
            emptyList()
        }
    }

    fun loadMapEventGroups(context: Context): MapEventGroupsSnapshot? {
        return try {
            val json = readJson(context, MAP_EVENT_GROUPS_ASSET)
            val adapter = moshi.adapter(MapEventGroupsSnapshot::class.java)
            val snapshot = adapter.fromJson(json)
            Log.i(TAG, "Loaded ${snapshot?.groups?.size ?: 0} map event groups from $MAP_EVENT_GROUPS_ASSET")
            snapshot
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load $MAP_EVENT_GROUPS_ASSET", error)
            null
        }
    }

    fun loadRegionAttackTotals(context: Context): RegionAttackTotalsSnapshot? {
        return try {
            val json = readJson(context, REGION_ATTACK_TOTALS_ASSET)
            val adapter = moshi.adapter(RegionAttackTotalsSnapshot::class.java)
            val snapshot = adapter.fromJson(json)
            Log.i(TAG, "Loaded ${snapshot?.regions?.size ?: 0} region attack totals from $REGION_ATTACK_TOTALS_ASSET")
            snapshot
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load $REGION_ATTACK_TOTALS_ASSET", error)
            null
        }
    }

    fun loadFuelRestrictions(context: Context): FuelRestrictionsSnapshot? {
        return try {
            val json = readJson(context, FUEL_RESTRICTIONS_ASSET)
            moshi.adapter(FuelRestrictionsSnapshot::class.java).fromJson(json)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load $FUEL_RESTRICTIONS_ASSET", error)
            null
        }
    }

    fun loadRegionalBudgetStress(context: Context): RegionalBudgetStressSnapshot? {
        return try {
            val json = readJson(context, REGIONAL_BUDGET_STRESS_ASSET)
            moshi.adapter(RegionalBudgetStressSnapshot::class.java).fromJson(json)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load $REGIONAL_BUDGET_STRESS_ASSET", error)
            null
        }
    }

    fun loadSpecialOperations(context: Context): SpecialOperationsSnapshot? {
        return try {
            val json = readJson(context, SPECIAL_OPERATIONS_ASSET)
            moshi.adapter(SpecialOperationsSnapshot::class.java).fromJson(json)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load $SPECIAL_OPERATIONS_ASSET", error)
            null
        }
    }

    fun loadLossTotals(context: Context): LossTotalsSnapshot? {
        return try {
            val json = readJson(context, LOSS_TOTALS_ASSET)
            moshi.adapter(LossTotalsSnapshot::class.java).fromJson(json)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load $LOSS_TOTALS_ASSET", error)
            null
        }
    }
}
