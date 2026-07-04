package io.github.ichwars.fitorb.relay.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RelayDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun relayBatchSerializesWithContractFieldNames() {
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
                    source = "android_relay",
                    capturedAt = "2026-07-03T09:55:05Z",
                    localDate = "2026-07-03",
                    protocolVersion = 1,
                )
            ),
        )

        val encoded = json.encodeToString(RelayBatchDto.serializer(), batch)

        assertEquals(true, encoded.contains("\"relay_id\":\"pixel-8\""))
        assertEquals(true, encoded.contains("\"sample_id\":\"sample-heart-1\""))
        assertEquals(true, encoded.contains("\"metric\":\"heart_rate\""))
        assertEquals(true, encoded.contains("\"source\":\"android_relay\""))
    }

    @Test
    fun relayBatchSerializesBacklogWhenSet() {
        val batch = RelayBatchDto(
            relayId = "pixel-8",
            ringId = "AA:BB:CC:DD:EE:FF",
            appVersion = "0.1.0",
            protocolVersion = 1,
            sentAt = "2026-07-03T10:00:00Z",
            samples = emptyList(),
            backlog = 2,
        )

        val encoded = json.encodeToString(RelayBatchDto.serializer(), batch)

        assertEquals(true, encoded.contains("\"backlog\":2"))
    }
}
