package io.github.ichwars.fitorb.relay.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RelayDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun relayBatchSerializesWithExactContractFieldNames() {
        val batch = RelayBatchDto(
            relayId = "pixel-8",
            ringId = "AA:BB:CC:DD:EE:FF",
            appVersion = "0.1.0",
            protocolVersion = 1,
            sentAt = "2026-07-03T10:00:00Z",
            samples = listOf(
                RelaySampleDto(
                    sampleId = "sample-heart-1",
                    ringId = "AA:BB:CC:DD:EE:FF",
                    metric = "heart_rate",
                    timestamp = "2026-07-03T09:55:00Z",
                    value = RelaySampleValue.IntValue(72),
                    unit = "bpm",
                    capturedAt = "2026-07-03T09:55:05Z",
                    localDate = "2026-07-03",
                )
            ),
        )

        val encoded = json.encodeToString(RelayBatchDto.serializer(), batch)

        assertEquals(
            JsonObject(
                mapOf(
                    "relay_id" to JsonPrimitive("pixel-8"),
                    "ring_id" to JsonPrimitive("AA:BB:CC:DD:EE:FF"),
                    "app_version" to JsonPrimitive("0.1.0"),
                    "protocol_version" to JsonPrimitive(1),
                    "sent_at" to JsonPrimitive("2026-07-03T10:00:00Z"),
                    "samples" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "sample_id" to JsonPrimitive("sample-heart-1"),
                                    "ring_id" to JsonPrimitive("AA:BB:CC:DD:EE:FF"),
                                    "metric" to JsonPrimitive("heart_rate"),
                                    "timestamp" to JsonPrimitive("2026-07-03T09:55:00Z"),
                                    "value" to JsonPrimitive(72),
                                    "unit" to JsonPrimitive("bpm"),
                                    "source" to JsonPrimitive("android_relay"),
                                    "captured_at" to JsonPrimitive("2026-07-03T09:55:05Z"),
                                    "local_date" to JsonPrimitive("2026-07-03"),
                                )
                            )
                        )
                    ),
                )
            ),
            json.parseToJsonElement(encoded),
        )
    }

    @Test
    fun defaultSourceIsSerialized() {
        val encoded = json.encodeToString(RelaySampleDto.serializer(), sample())

        val decodedJson = json.parseToJsonElement(encoded).jsonObject

        assertEquals("android_relay", decodedJson["source"]?.jsonPrimitive?.content)
    }

    @Test
    fun missingSourceFailsToDecode() {
        val payload = """
            {
              "sample_id": "sample-heart-1",
              "ring_id": "AA:BB:CC:DD:EE:FF",
              "metric": "heart_rate",
              "timestamp": "2026-07-03T09:55:00Z",
              "value": 72,
              "captured_at": "2026-07-03T09:55:05Z"
            }
        """.trimIndent()

        assertFailsWith<SerializationException> {
            json.decodeFromString(RelaySampleDto.serializer(), payload)
        }
    }

    @Test
    fun backlogIsOmittedWhenNullAndSerializedWhenSet() {
        val withoutBacklog = json.parseToJsonElement(
            json.encodeToString(RelayBatchDto.serializer(), batch(backlog = null))
        ).jsonObject
        val withBacklog = json.parseToJsonElement(
            json.encodeToString(RelayBatchDto.serializer(), batch(backlog = 2))
        ).jsonObject

        assertFalse(withoutBacklog.containsKey("backlog"))
        assertEquals(2, withBacklog["backlog"]?.jsonPrimitive?.int)
    }

    @Test
    fun nullableSampleFieldsAreOmittedWhenNull() {
        val encoded = json.encodeToString(RelaySampleDto.serializer(), sample())

        val decodedJson = json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            setOf("sample_id", "ring_id", "metric", "timestamp", "value", "source", "captured_at"),
            decodedJson.keys,
        )
        assertFalse(decodedJson.containsKey("unit"))
        assertFalse(decodedJson.containsKey("local_date"))
        assertFalse(decodedJson.containsKey("uploaded_at"))
        assertFalse(decodedJson.containsKey("raw_hex"))
    }

    @Test
    fun sampleValueSerializesPrimitiveJsonValues() {
        assertPrimitiveJson(JsonPrimitive(72), RelaySampleValue.IntValue(72))
        assertPrimitiveJson(JsonPrimitive(72.5), RelaySampleValue.DoubleValue(72.5))
        assertPrimitiveJson(JsonPrimitive("72"), RelaySampleValue.StringValue("72"))
        assertPrimitiveJson(JsonPrimitive(true), RelaySampleValue.BoolValue(true))
    }

    @Test
    fun sampleValueDeserializesPrimitiveJsonValues() {
        assertEquals(
            RelaySampleValue.IntValue(72),
            json.decodeFromString(RelaySampleValueSerializer, "72"),
        )
        assertEquals(
            RelaySampleValue.DoubleValue(72.5),
            json.decodeFromString(RelaySampleValueSerializer, "72.5"),
        )
        assertEquals(
            RelaySampleValue.BoolValue(true),
            json.decodeFromString(RelaySampleValueSerializer, "true"),
        )
        assertEquals(
            RelaySampleValue.StringValue("72"),
            json.decodeFromString(RelaySampleValueSerializer, "\"72\""),
        )
        assertEquals(
            RelaySampleValue.StringValue("true"),
            json.decodeFromString(RelaySampleValueSerializer, "\"true\""),
        )
    }

    @Test
    fun sampleValueRejectsNullAndStructuredJsonValues() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(RelaySampleValueSerializer, "null")
        }
        assertFailsWith<SerializationException> {
            json.decodeFromString(RelaySampleValueSerializer, "{}")
        }
        assertFailsWith<SerializationException> {
            json.decodeFromString(RelaySampleValueSerializer, "[]")
        }
    }

    @Test
    fun relayAckSerializesWithExactContractFieldNames() {
        val ack = RelayAckDto(
            accepted = listOf("sample-heart-1"),
            duplicates = listOf("sample-heart-0"),
            rejected = listOf(
                RejectedSampleDto(
                    sampleId = "bad-sample",
                    reason = "invalid_metric",
                )
            ),
            serverTime = "2026-07-03T10:02:00Z",
        )

        val encoded = json.encodeToString(RelayAckDto.serializer(), ack)

        assertEquals(
            JsonObject(
                mapOf(
                    "accepted" to JsonArray(listOf(JsonPrimitive("sample-heart-1"))),
                    "duplicates" to JsonArray(listOf(JsonPrimitive("sample-heart-0"))),
                    "rejected" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "sample_id" to JsonPrimitive("bad-sample"),
                                    "reason" to JsonPrimitive("invalid_metric"),
                                )
                            )
                        )
                    ),
                    "server_time" to JsonPrimitive("2026-07-03T10:02:00Z"),
                )
            ),
            json.parseToJsonElement(encoded),
        )
    }

    @Test
    fun relayAckDecodesContractFieldNames() {
        val payload = """
            {
              "accepted": ["sample-heart-1"],
              "duplicates": ["sample-heart-0"],
              "rejected": [
                {
                  "sample_id": "bad-sample",
                  "reason": "invalid_metric"
                }
              ],
              "server_time": "2026-07-03T10:02:00Z"
            }
        """.trimIndent()

        val ack = json.decodeFromString(RelayAckDto.serializer(), payload)

        assertEquals(listOf("sample-heart-1"), ack.accepted)
        assertEquals(listOf("sample-heart-0"), ack.duplicates)
        assertEquals(
            listOf(RejectedSampleDto(sampleId = "bad-sample", reason = "invalid_metric")),
            ack.rejected,
        )
        assertEquals("2026-07-03T10:02:00Z", ack.serverTime)
    }

    private fun assertPrimitiveJson(
        expected: JsonElement,
        value: RelaySampleValue,
    ) {
        assertEquals(
            expected,
            json.parseToJsonElement(json.encodeToString(RelaySampleValueSerializer, value)),
        )
    }

    private fun batch(backlog: Int?) = RelayBatchDto(
        relayId = "pixel-8",
        ringId = "AA:BB:CC:DD:EE:FF",
        appVersion = "0.1.0",
        protocolVersion = 1,
        sentAt = "2026-07-03T10:00:00Z",
        samples = emptyList(),
        backlog = backlog,
    )

    private fun sample(value: RelaySampleValue = RelaySampleValue.IntValue(72)) = RelaySampleDto(
        sampleId = "sample-heart-1",
        ringId = "AA:BB:CC:DD:EE:FF",
        metric = "heart_rate",
        timestamp = "2026-07-03T09:55:00Z",
        value = value,
        capturedAt = "2026-07-03T09:55:05Z",
    )
}
