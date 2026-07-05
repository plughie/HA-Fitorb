package io.github.ichwars.fitorb.relay.ui

import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import java.time.Instant

data class RingDashboardSnapshot(
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val steps: Int? = null,
    val calories: Int? = null,
    val distanceMeters: Int? = null,
    val activityMinutes: Int? = null,
    val heartRate: Int? = null,
    val spo2: Int? = null,
    val stress: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepAsleepMinutes: Int? = null,
    val sleepAwakeMinutes: Int? = null,
    val sleepLightMinutes: Int? = null,
    val sleepDeepMinutes: Int? = null,
    val sleepRemMinutes: Int? = null,
) {
    val hasAnyRingData: Boolean
        get() = batteryPercent != null ||
            charging != null ||
            steps != null ||
            calories != null ||
            distanceMeters != null ||
            activityMinutes != null ||
            heartRate != null ||
            spo2 != null ||
            stress != null ||
            sleepMinutes != null ||
            sleepAsleepMinutes != null ||
            sleepAwakeMinutes != null ||
            sleepLightMinutes != null ||
            sleepDeepMinutes != null ||
            sleepRemMinutes != null

    companion object {
        fun from(samples: List<RelaySampleDto>): RingDashboardSnapshot =
            RingDashboardSnapshot(
                batteryPercent = samples.latestInt("battery"),
                charging = samples.latestBool("charging"),
                steps = samples.latestInt("steps"),
                calories = samples.latestInt("calories"),
                distanceMeters = samples.latestInt("distance"),
                activityMinutes = samples.latestInt("activity_minutes")
                    ?: samples.latestInt("active_minutes")
                    ?: samples.latestInt("duration"),
                heartRate = samples.latestInt("heart_rate"),
                spo2 = samples.latestInt("spo2"),
                stress = samples.latestInt("stress"),
                sleepMinutes = samples.latestInt("sleep_summary"),
                sleepAsleepMinutes = samples.latestInt("sleep_asleep"),
                sleepAwakeMinutes = samples.latestInt("sleep_awake"),
                sleepLightMinutes = samples.latestInt("sleep_light"),
                sleepDeepMinutes = samples.latestInt("sleep_deep"),
                sleepRemMinutes = samples.latestInt("sleep_rem"),
            )
    }
}

private fun List<RelaySampleDto>.latestInt(metric: String): Int? =
    latest(metric)?.value?.let { value ->
        when (value) {
            is RelaySampleValue.IntValue -> value.value
            is RelaySampleValue.DoubleValue -> value.value.toInt()
            is RelaySampleValue.StringValue -> value.value.toIntOrNull()
            is RelaySampleValue.BoolValue -> null
        }
    }

private fun List<RelaySampleDto>.latestBool(metric: String): Boolean? =
    latest(metric)?.value?.let { value ->
        when (value) {
            is RelaySampleValue.BoolValue -> value.value
            is RelaySampleValue.StringValue -> value.value.toBooleanStrictOrNull()
            else -> null
        }
    }

private fun List<RelaySampleDto>.latest(metric: String): RelaySampleDto? =
    filter { it.metric == metric }
        .maxByOrNull { sample -> sample.timestampInstant() }

private fun RelaySampleDto.timestampInstant(): Instant =
    runCatching { Instant.parse(timestamp) }.getOrDefault(Instant.EPOCH)
