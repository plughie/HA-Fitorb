package io.github.ichwars.fitorb.relay.ui

import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RingDashboardSnapshotTest {
    @Test
    fun latestRealSamplesDriveDashboardValues() {
        val snapshot = RingDashboardSnapshot.from(
            listOf(
                sample("battery", "2026-07-05T07:00:00Z", RelaySampleValue.IntValue(81), "%"),
                sample("battery", "2026-07-05T07:05:00Z", RelaySampleValue.IntValue(88), "%"),
                sample("charging", "2026-07-05T07:05:00Z", RelaySampleValue.BoolValue(false)),
                sample("steps", "2026-07-05T00:00:00Z", RelaySampleValue.IntValue(6421)),
                sample("calories", "2026-07-05T00:00:00Z", RelaySampleValue.IntValue(312), "kcal"),
                sample("distance", "2026-07-05T00:00:00Z", RelaySampleValue.IntValue(4870), "m"),
                sample("activity_minutes", "2026-07-05T00:00:00Z", RelaySampleValue.IntValue(74), "min"),
                sample("heart_rate", "2026-07-05T07:02:00Z", RelaySampleValue.IntValue(73), "bpm"),
                sample("spo2", "2026-07-05T07:02:00Z", RelaySampleValue.IntValue(97), "%"),
                sample("stress", "2026-07-05T07:02:00Z", RelaySampleValue.IntValue(28)),
                sample("sleep_summary", "2026-07-05T06:30:00Z", RelaySampleValue.IntValue(426), "min"),
                sample("sleep_asleep", "2026-07-05T06:30:00Z", RelaySampleValue.IntValue(390), "min"),
                sample("sleep_awake", "2026-07-05T06:30:00Z", RelaySampleValue.IntValue(36), "min"),
                sample("sleep_light", "2026-07-05T06:30:00Z", RelaySampleValue.IntValue(220), "min"),
                sample("sleep_deep", "2026-07-05T06:30:00Z", RelaySampleValue.IntValue(95), "min"),
                sample("sleep_rem", "2026-07-05T06:30:00Z", RelaySampleValue.IntValue(75), "min"),
            ),
        )

        assertEquals(88, snapshot.batteryPercent)
        assertFalse(snapshot.charging!!)
        assertEquals(6421, snapshot.steps)
        assertEquals(312, snapshot.calories)
        assertEquals(4870, snapshot.distanceMeters)
        assertEquals(74, snapshot.activityMinutes)
        assertEquals(73, snapshot.heartRate)
        assertEquals(97, snapshot.spo2)
        assertEquals(28, snapshot.stress)
        assertEquals(426, snapshot.sleepMinutes)
        assertEquals(390, snapshot.sleepAsleepMinutes)
        assertEquals(36, snapshot.sleepAwakeMinutes)
        assertEquals(220, snapshot.sleepLightMinutes)
        assertEquals(95, snapshot.sleepDeepMinutes)
        assertEquals(75, snapshot.sleepRemMinutes)
    }

    @Test
    fun emptySnapshotDoesNotInventValues() {
        val snapshot = RingDashboardSnapshot.from(emptyList())

        assertNull(snapshot.batteryPercent)
        assertNull(snapshot.steps)
        assertNull(snapshot.heartRate)
        assertFalse(snapshot.hasAnyRingData)
    }

    private fun sample(
        metric: String,
        timestamp: String,
        value: RelaySampleValue,
        unit: String? = null,
    ): RelaySampleDto =
        RelaySampleDto(
            sampleId = "$metric-$timestamp",
            ringId = "AA:BB:CC:DD:EE:FF",
            metric = metric,
            timestamp = timestamp,
            value = value,
            unit = unit,
            capturedAt = timestamp,
        )
}
