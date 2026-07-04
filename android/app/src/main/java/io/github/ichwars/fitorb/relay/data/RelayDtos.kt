package io.github.ichwars.fitorb.relay.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RelayBatchDto(
    @SerialName("relay_id") val relayId: String,
    @SerialName("ring_id") val ringId: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("protocol_version") val protocolVersion: Int,
    @SerialName("sent_at") val sentAt: String,
    val samples: List<RelaySampleDto>,
    val backlog: Int? = null,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class RelaySampleDto(
    @SerialName("sample_id") val sampleId: String,
    @SerialName("ring_id") val ringId: String,
    val metric: String,
    val timestamp: String,
    val value: RelaySampleValue,
    val unit: String? = null,
    @EncodeDefault
    val source: String = "android_relay",
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("local_date") val localDate: String? = null,
    @SerialName("uploaded_at") val uploadedAt: String? = null,
    @SerialName("raw_hex") val rawHex: String? = null,
    @SerialName("protocol_version") val protocolVersion: Int = 1,
)

@Serializable(with = RelaySampleValueSerializer::class)
sealed interface RelaySampleValue {
    val jsonElement: JsonElement

    data class IntValue(val value: Int) : RelaySampleValue {
        override val jsonElement: JsonElement = JsonPrimitive(value)
    }

    data class DoubleValue(val value: Double) : RelaySampleValue {
        override val jsonElement: JsonElement = JsonPrimitive(value)
    }

    data class StringValue(val value: String) : RelaySampleValue {
        override val jsonElement: JsonElement = JsonPrimitive(value)
    }

    data class BoolValue(val value: Boolean) : RelaySampleValue {
        override val jsonElement: JsonElement = JsonPrimitive(value)
    }
}

@Serializable
data class RelayAckDto(
    val accepted: List<String>,
    val duplicates: List<String>,
    val rejected: List<RejectedSampleDto>,
    @SerialName("server_time") val serverTime: String,
)

@Serializable
data class RejectedSampleDto(
    @SerialName("sample_id") val sampleId: String,
    val reason: String,
)
