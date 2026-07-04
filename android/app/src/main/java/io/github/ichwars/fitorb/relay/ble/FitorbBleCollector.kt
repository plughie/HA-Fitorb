package io.github.ichwars.fitorb.relay.ble

import io.github.ichwars.fitorb.relay.data.RelaySampleDto

interface FitorbBleCollector {
    suspend fun collectOnce(ringId: String): List<RelaySampleDto>
}

class AndroidFitorbBleCollector : FitorbBleCollector {
    override suspend fun collectOnce(ringId: String): List<RelaySampleDto> {
        return emptyList()
    }
}
