package com.familyguard.app.data.local

import android.content.Context
import androidx.room.*
import com.familyguard.app.data.model.DnsQueryBatch
import com.familyguard.app.data.model.LocationRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Offline buffer: GPS points and DNS log entries wait here until the server
 * accepts them, so nothing is lost without internet or when a service stops.
 * Both tables are capped; the oldest rows are dropped first.
 */
@Entity(tableName = "pending_locations")
data class PendingLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val altitude: Double?,
    val speed: Double?,
    val batteryLevel: Int?,
    val recordedAt: String
) {
    fun toRequest() = LocationRequest(
        latitude, longitude, accuracy, altitude, speed, batteryLevel, recordedAt
    )
}

@Entity(tableName = "pending_dns_queries")
data class PendingDnsQuery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val queryType: String,
    val wasBlocked: Boolean,
    val timestamp: String
) {
    fun toQuery() = DnsQueryBatch.Query(domain, queryType, wasBlocked, timestamp)
}

@Dao
interface PendingLocationDao {
    @Insert
    suspend fun insert(location: PendingLocation)

    @Query("SELECT * FROM pending_locations ORDER BY id LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingLocation>

    @Query("DELETE FROM pending_locations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_locations WHERE id NOT IN (SELECT id FROM pending_locations ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface PendingDnsQueryDao {
    @Insert
    suspend fun insertAll(queries: List<PendingDnsQuery>)

    @Query("SELECT * FROM pending_dns_queries ORDER BY id LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingDnsQuery>

    @Query("DELETE FROM pending_dns_queries WHERE id <= :lastId")
    suspend fun deleteUpTo(lastId: Long)

    @Query("DELETE FROM pending_dns_queries WHERE id NOT IN (SELECT id FROM pending_dns_queries ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Database(entities = [PendingLocation::class, PendingDnsQuery::class], version = 1, exportSchema = false)
abstract class OfflineBufferDatabase : RoomDatabase() {
    abstract fun locations(): PendingLocationDao
    abstract fun dnsQueries(): PendingDnsQueryDao
}

@Module
@InstallIn(SingletonComponent::class)
object OfflineBufferModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OfflineBufferDatabase =
        Room.databaseBuilder(context, OfflineBufferDatabase::class.java, "offline_buffer.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideLocationDao(db: OfflineBufferDatabase): PendingLocationDao = db.locations()

    @Provides
    fun provideDnsQueryDao(db: OfflineBufferDatabase): PendingDnsQueryDao = db.dnsQueries()
}
