package io.github.ichwars.fitorb.relay.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RelaySampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQueued(sample: RelaySampleEntity)

    @Query("SELECT * FROM relay_samples WHERE delivered = 0 AND rejectedReason IS NULL ORDER BY timestamp LIMIT :limit")
    suspend fun pendingBatch(limit: Int): List<RelaySampleEntity>

    @Query("SELECT COUNT(*) FROM relay_samples WHERE delivered = 0 AND rejectedReason IS NULL")
    suspend fun pendingCount(): Int

    @Query("SELECT * FROM relay_samples WHERE delivered = 0 AND rejectedReason IS NULL AND ringId = :ringId ORDER BY timestamp LIMIT :limit")
    suspend fun pendingBatchForRing(ringId: String, limit: Int): List<RelaySampleEntity>

    @Query("SELECT COUNT(*) FROM relay_samples WHERE delivered = 0 AND rejectedReason IS NULL AND ringId = :ringId")
    suspend fun pendingCountForRing(ringId: String): Int

    @Query("SELECT * FROM relay_samples WHERE rejectedReason IS NULL AND ringId = :ringId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latestSamplesForRing(ringId: String, limit: Int): List<RelaySampleEntity>

    @Query("UPDATE relay_samples SET delivered = 1 WHERE sampleId IN (:sampleIds) AND rejectedReason IS NULL")
    suspend fun markDelivered(sampleIds: List<String>)

    @Query("UPDATE relay_samples SET rejectedReason = :reason WHERE sampleId = :sampleId AND delivered = 0")
    suspend fun markRejected(sampleId: String, reason: String)
}
