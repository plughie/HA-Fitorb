package io.github.ichwars.fitorb.relay.settings

data class RelaySettings(
    val homeAssistantUrl: String,
    val relayToken: String,
    val relayId: String,
    val ringId: String,
    val syncIntervalMinutes: Int = 10,
    val ringName: String = "",
    val stepGoal: Int = DEFAULT_STEP_GOAL_STEPS,
)

const val MIN_STEP_GOAL_STEPS = 1_000
const val MAX_STEP_GOAL_STEPS = 50_000
const val DEFAULT_STEP_GOAL_STEPS = 10_000
const val STEP_GOAL_INCREMENT_STEPS = 500
