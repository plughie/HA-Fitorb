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

    @Query("UPDATE relay_samples SET delivered = 1 WHERE sampleId IN (:sampleIds)")
    suspend fun markDelivered(sampleIds: List<String>)

    @Query("UPDATE relay_samples SET rejectedReason = :reason WHERE sampleId = :sampleId")
    suspend fun markRejected(sampleId: String, reason: String)
}
