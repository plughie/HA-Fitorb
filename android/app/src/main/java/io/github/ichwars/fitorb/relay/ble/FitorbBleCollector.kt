package io.github.ichwars.fitorb.relay.ble

import io.github.ichwars.fitorb.relay.data.RelaySampleDto

/**
 * Boundary for BLE collection. A real scanner/connector should implement this interface later.
 */
interface FitorbBleCollector {
    suspend fun collectOnce(ringId: String): List<RelaySampleDto>
}

/**
 * Deliberate no-op implementation that models a ring-not-visible/no-data collection result.
 */
class NoopFitorbBleCollector : FitorbBleCollector {
    override suspend fun collectOnce(ringId: String): List<RelaySampleDto> {
        return emptyList()
    }
}
