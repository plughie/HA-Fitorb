package io.github.ichwars.fitorb.relay.settings

data class RelaySettings(
    val homeAssistantUrl: String,
    val relayToken: String,
    val relayId: String,
    val ringId: String,
    val syncIntervalMinutes: Int = 10,
)
