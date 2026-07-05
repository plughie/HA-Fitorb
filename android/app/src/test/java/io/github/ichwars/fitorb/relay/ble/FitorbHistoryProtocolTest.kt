package io.github.ichwars.fitorb.relay.ble

import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FitorbHistoryProtocolTest {
    @Test
    fun buildHeartRateHistoryCommandUsesMidnightEpoch() {
        val command = FitorbHistoryProtocol.buildHeartRateHistoryCommand(
            LocalDate.of(2026, 6, 26),
            ZoneOffset.UTC,
        )

        assertContentEquals(
            packet(0x15, 0x00, 0xC1, 0x3D, 0x6A, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x7D),
            command,
        )
    }

    @Test
    fun buildBigDataRequestUsesColmiFrame() {
        assertContentEquals(
            packet(0xBC, 0x27, 0, 0, 0xFF, 0xFF),
            FitorbHistoryProtocol.buildBigDataRequest(0x27),
        )
    }

    @Test
    fun bigDataFrameParserReassemblesSplitChunks() {
        val parser = BigDataFrameParser()
        val payload = packet(1, 0, 6, 100, 5, 200, 1, 2, 30)
        val first = packet(0xBC, 0x27, payload.size, 0, 0x79)
        val second = packet(0xED) + payload

        assertTrue(parser.consume(first).isEmpty())
        val frames = parser.consume(second)

        assertEquals(1, frames.size)
        assertEquals(0x27, frames.single().dataId)
        assertContentEquals(payload, frames.single().payload)
    }

    @Test
    fun parseSleepPayloadCreatesStageSamplesAndSummary() {
        val payload = packet(
            1,
            0, 8, 100, 5, 200, 1, 2, 30, 3, 18,
        )

        val result = FitorbHistoryProtocol.parseSleepPayload(
            payload,
            today = LocalDate.of(2026, 6, 26),
        )

        assertEquals(8, result.samples.size)
        assertEquals("sleep_stage", result.samples[0].metric)
        assertEquals(Instant.parse("2026-06-26T23:00:00Z"), result.samples[0].timestamp)
        assertEquals(RelaySampleValue.StringValue("light"), result.samples[0].value)
        val summary = result.samples.single { it.metric == "sleep_summary" }
        assertEquals(RelaySampleValue.IntValue(516), summary.value)
        assertEquals("2026-06-26", summary.localDate)
        assertEquals(RelaySampleValue.IntValue(516), result.samples.single { it.metric == "sleep_asleep" }.value)
        assertEquals(RelaySampleValue.IntValue(0), result.samples.single { it.metric == "sleep_awake" }.value)
        assertEquals(RelaySampleValue.IntValue(30), result.samples.single { it.metric == "sleep_light" }.value)
        assertEquals(RelaySampleValue.IntValue(18), result.samples.single { it.metric == "sleep_deep" }.value)
        assertEquals(RelaySampleValue.IntValue(0), result.samples.single { it.metric == "sleep_rem" }.value)
    }

    @Test
    fun heartRateParserDecodesCapturedPackets() {
        val parser = HeartRateHistoryParser()
        val packets = listOf(
            packet(21, 0, 24, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 50),
            packet(21, 1, 128, 105, 142, 105, 91, 0, 0, 0, 0, 0, 94, 0, 0, 175),
            packet(21, 2, 0, 0, 0, 61, 0, 0, 0, 0, 0, 58, 0, 0, 0, 142),
            packet(21, 3, 0, 0, 55, 0, 0, 0, 0, 0, 60, 0, 0, 0, 0, 139),
            packet(21, 4, 0, 68, 0, 0, 0, 0, 0, 56, 0, 0, 0, 0, 0, 149),
            packet(21, 5, 67, 0, 0, 0, 0, 0, 62, 0, 0, 0, 0, 0, 69, 224),
            packet(21, 6, 0, 0, 0, 0, 0, 57, 0, 0, 0, 0, 0, 98, 0, 182),
            packet(21, 7, 0, 0, 0, 0, 73, 0, 0, 0, 0, 0, 62, 0, 0, 163),
            packet(21, 8, 0, 0, 0, 99, 0, 0, 0, 0, 0, 68, 0, 0, 0, 196),
            packet(21, 9, 0, 0, 85, 0, 0, 0, 0, 0, 93, 0, 0, 0, 0, 208),
            packet(21, 10, 0, 87, 0, 0, 0, 0, 0, 81, 0, 0, 0, 0, 0, 199),
            packet(21, 11, 85, 0, 0, 0, 0, 0, 94, 0, 0, 0, 0, 0, 77, 32),
            packet(21, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 33),
            packet(21, 13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 34),
            packet(21, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 35),
            packet(21, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 36),
            packet(21, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 37),
            packet(21, 17, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 38),
            packet(21, 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 39),
            packet(21, 19, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 40),
            packet(21, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 41),
            packet(21, 21, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 42),
            packet(21, 22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 43),
            packet(21, 23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 44),
        )

        val parsed = packets.mapNotNull(parser::consume).last()

        assertEquals(24, parsed.size)
        assertEquals(listOf(91, 94, 61, 58), parsed.take(4).map { (it.value as RelaySampleValue.IntValue).value })
        assertEquals(Instant.parse("2026-02-13T00:00:00Z"), parsed.first().timestamp)
        assertEquals("bpm", parsed.first().unit)
    }

    @Test
    fun collectedSampleBuildsStableRelayDto() {
        val sample = RingCollectedSample(
            metric = "heart_rate",
            timestamp = Instant.parse("2026-07-03T09:55:00Z"),
            value = RelaySampleValue.IntValue(72),
            unit = "bpm",
            localDate = "2026-07-03",
        )

        val dto = sample.toRelaySampleDto(
            ringId = "AA:BB:CC:DD:EE:FF",
            capturedAt = Instant.parse("2026-07-03T09:55:05Z"),
        )

        assertEquals("heart_rate", dto.metric)
        assertEquals("AA:BB:CC:DD:EE:FF", dto.ringId)
        assertEquals("2026-07-03T09:55:00Z", dto.timestamp)
        assertIs<RelaySampleValue.IntValue>(dto.value)
        assertEquals(dto.sampleId, sample.toRelaySampleDto("AA:BB:CC:DD:EE:FF", Instant.parse("2026-07-03T10:00:00Z")).sampleId)
    }

    private fun packet(vararg bytes: Int): ByteArray = bytes.map { it.toByte() }.toByteArray()
}
