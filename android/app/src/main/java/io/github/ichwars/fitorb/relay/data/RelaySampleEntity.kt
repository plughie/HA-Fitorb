package io.github.ichwars.fitorb.relay.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Entity(
    tableName = "relay_samples",
    indices = [
        Index(value = ["delivered", "rejectedReason", "timestamp"]),
    ],
)
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

fun RelaySampleDto.toEntity(): RelaySampleEntity =
    RelaySampleEntity(
        sampleId = sampleId,
        ringId = ringId,
        metric = metric,
        timestamp = timestamp,
        valueJson = value.jsonElement.toString(),
        unit = unit,
        capturedAt = capturedAt,
    )

fun RelaySampleEntity.toDto(): RelaySampleDto =
    RelaySampleDto(
        sampleId = sampleId,
        ringId = ringId,
        metric = metric,
        timestamp = timestamp,
        value = valueJson.toRelaySampleValue(),
        unit = unit,
        capturedAt = capturedAt,
    )

private fun String.toRelaySampleValue(): RelaySampleValue {
    val primitive = Json.parseToJsonElement(this).jsonPrimitive
    if (primitive.isString) {
        return RelaySampleValue.StringValue(primitive.content)
    }
    primitive.booleanOrNull?.let { return RelaySampleValue.BoolValue(it) }
    primitive.intOrNull?.let { return RelaySampleValue.IntValue(it) }
    primitive.doubleOrNull?.let { return RelaySampleValue.DoubleValue(it) }
    return RelaySampleValue.StringValue(primitive.content)
}
