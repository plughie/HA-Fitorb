package io.github.ichwars.fitorb.relay.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "relay_samples")
data class RelaySampleEntity(
    @PrimaryKey val sampleId: String,
    val ringId: String,
    val metric: String,
    val timestamp: String,
    val valueJson: String,
    val unit: String?,
    val capturedAt: String,
    val delivered: Boolean = false,
    val rejectedReason: String? = null,
)
