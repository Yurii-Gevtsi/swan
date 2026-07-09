package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY date DESC")
    fun getAllEvents(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: String): EventEntity?

    @Query("DELETE FROM events")
    suspend fun clearAllEvents()
}

@Dao
interface RegionDao {
    @Query("SELECT * FROM regions ORDER BY nameEn ASC")
    fun getAllRegions(): Flow<List<RegionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegions(regions: List<RegionEntity>)

    @Query("SELECT * FROM regions WHERE regionId = :id")
    suspend fun getRegionById(id: String): RegionEntity?

    @Query("DELETE FROM regions")
    suspend fun clearAllRegions()
}

@Dao
interface MaritimeAreaDao {
    @Query("SELECT * FROM maritime_areas ORDER BY nameEn ASC")
    fun getAllMaritimeAreas(): Flow<List<MaritimeAreaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaritimeAreas(areas: List<MaritimeAreaEntity>)

    @Query("SELECT * FROM maritime_areas WHERE maritimeAreaId = :id")
    suspend fun getMaritimeAreaById(id: String): MaritimeAreaEntity?

    @Query("DELETE FROM maritime_areas")
    suspend fun clearAllMaritimeAreas()
}

@Dao
interface SanctionsJurisdictionDao {
    @Query("SELECT * FROM sanctions_jurisdictions")
    fun getAllSanctionsJurisdictions(): Flow<List<SanctionsJurisdictionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSanctionsJurisdictions(jurisdictions: List<SanctionsJurisdictionEntity>)

    @Query("DELETE FROM sanctions_jurisdictions")
    suspend fun clearAllSanctionsJurisdictions()
}

@Dao
interface MaritimeAssetDao {
    @Query("SELECT * FROM maritime_assets")
    fun getAllMaritimeAssets(): Flow<List<MaritimeAssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaritimeAssets(assets: List<MaritimeAssetEntity>)

    @Query("SELECT * FROM maritime_assets WHERE assetId = :id")
    suspend fun getMaritimeAssetById(id: String): MaritimeAssetEntity?

    @Query("DELETE FROM maritime_assets")
    suspend fun clearAllMaritimeAssets()
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY sourceName ASC")
    fun getAllSources(): Flow<List<SourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Query("SELECT * FROM sources WHERE sourceId = :id")
    suspend fun getSourceById(id: String): SourceEntity?

    @Query("DELETE FROM sources")
    suspend fun clearAllSources()
}

@Database(
    entities = [
        EventEntity::class,
        RegionEntity::class,
        MaritimeAreaEntity::class,
        SanctionsJurisdictionEntity::class,
        MaritimeAssetEntity::class,
        SourceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun regionDao(): RegionDao
    abstract fun maritimeAreaDao(): MaritimeAreaDao
    abstract fun sanctionsJurisdictionDao(): SanctionsJurisdictionDao
    abstract fun maritimeAssetDao(): MaritimeAssetDao
    abstract fun sourceDao(): SourceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "black_swan_database"
                )
                .addCallback(DatabasePrepopulateCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabasePrepopulateCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val database = getDatabase(context)
                database.sourceDao().insertSources(SampleOsintData.sources)
                database.regionDao().insertRegions(SampleOsintData.regions)
                database.maritimeAreaDao().insertMaritimeAreas(SampleOsintData.maritimeAreas)
                database.sanctionsJurisdictionDao().insertSanctionsJurisdictions(SampleOsintData.sanctionsJurisdictions)
                database.maritimeAssetDao().insertMaritimeAssets(SampleOsintData.maritimeAssets)
                database.eventDao().insertEvents(SampleOsintData.events)
            }
        }
    }
}
