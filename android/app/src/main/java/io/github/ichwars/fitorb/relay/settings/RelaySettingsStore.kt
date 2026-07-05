package io.github.ichwars.fitorb.relay.settings

import android.content.Context

private const val PREFERENCES_NAME = "fitorb_relay_settings"
private const val KEY_HOME_ASSISTANT_URL = "home_assistant_url"
private const val KEY_RELAY_TOKEN = "relay_token"
private const val KEY_RELAY_ID = "relay_id"
private const val KEY_RING_ID = "ring_id"
private const val KEY_RING_NAME = "ring_name"
private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
private const val KEY_STEP_GOAL = "step_goal"

class RelaySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): RelaySettings = RelaySettings(
        homeAssistantUrl = preferences.getString(KEY_HOME_ASSISTANT_URL, null).orEmpty(),
        relayToken = preferences.getString(KEY_RELAY_TOKEN, null).orEmpty(),
        relayId = preferences.getString(KEY_RELAY_ID, null).orEmpty(),
        ringId = preferences.getString(KEY_RING_ID, null).orEmpty(),
        ringName = preferences.getString(KEY_RING_NAME, null).orEmpty(),
        syncIntervalMinutes = preferences.getInt(
            KEY_SYNC_INTERVAL_MINUTES,
            DEFAULT_SYNC_INTERVAL_MINUTES,
        ).coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES),
        stepGoal = preferences.getInt(
            KEY_STEP_GOAL,
            DEFAULT_STEP_GOAL_STEPS,
        ).coerceIn(MIN_STEP_GOAL_STEPS, MAX_STEP_GOAL_STEPS),
    )

    fun save(settings: RelaySettings) {
        preferences.edit()
            .putString(KEY_HOME_ASSISTANT_URL, settings.homeAssistantUrl.trim())
            .putString(KEY_RELAY_TOKEN, settings.relayToken.trim())
            .putString(KEY_RELAY_ID, settings.relayId.trim())
            .putString(KEY_RING_ID, settings.ringId.trim())
            .putString(KEY_RING_NAME, settings.ringName.trim())
            .putInt(
                KEY_SYNC_INTERVAL_MINUTES,
                settings.syncIntervalMinutes.coerceIn(
                    MIN_SYNC_INTERVAL_MINUTES,
                    MAX_SYNC_INTERVAL_MINUTES,
                ),
            )
            .putInt(
                KEY_STEP_GOAL,
                settings.stepGoal.coerceIn(
                    MIN_STEP_GOAL_STEPS,
                    MAX_STEP_GOAL_STEPS,
                ),
            )
            .apply()
    }
}

const val MIN_SYNC_INTERVAL_MINUTES = 1
const val MAX_SYNC_INTERVAL_MINUTES = 60
const val DEFAULT_SYNC_INTERVAL_MINUTES = 10
