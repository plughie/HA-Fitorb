package io.github.ichwars.fitorb.relay.sync

object SyncPolicy {
    fun nextDelayMinutes(failures: Int, configuredIntervalMinutes: Int): Long {
        val base = configuredIntervalMinutes.coerceIn(1, 60)
        if (failures <= 0) return base.toLong()
        val multiplier = 1 shl failures.coerceAtMost(3)
        return (base * multiplier).coerceAtMost(60).toLong()
    }

    fun shouldStretchForLowRingBattery(ringBatteryPercent: Int?): Boolean {
        return ringBatteryPercent != null && ringBatteryPercent < 20
    }
}
