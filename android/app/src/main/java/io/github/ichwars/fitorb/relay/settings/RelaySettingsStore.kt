package io.github.ichwars.fitorb.relay.settings

import android.content.Context
import io.github.ichwars.fitorb.relay.BuildConfig

private const val PREFERENCES_NAME = "fitorb_relay_settings"
private const val KEY_HOME_ASSISTANT_URL = "home_assistant_url"
private const val KEY_RELAY_TOKEN = "relay_token"
private const val KEY_RELAY_ID = "relay_id"
private const val KEY_RING_ID = "ring_id"
private const val KEY_RING_NAME = "ring_name"
private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
private const val KEY_STEP_GOAL = "step_goal"
private const val KEY_LAST_SEND_AT = "last_send_at"
private const val KEY_LAST_SENT = "last_sent"
private const val KEY_LAST_ACCEPTED = "last_accepted"
private const val KEY_LAST_DUPLICATES = "last_duplicates"
private const val KEY_LAST_REJECTED = "last_rejected"

val DEFAULT_HOME_ASSISTANT_URL: String = BuildConfig.DEFAULT_HOME_ASSISTANT_URL
val DEFAULT_RELAY_TOKEN: String = BuildConfig.DEFAULT_RELAY_TOKEN
val DEFAULT_RING_ID: String = BuildConfig.DEFAULT_RING_ID

class RelaySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): RelaySettings = RelaySettings(
        homeAssistantUrl = preferences.getString(
            KEY_HOME_ASSISTANT_URL,
            DEFAULT_HOME_ASSISTANT_URL,
        ).orEmpty(),
        relayToken = preferences.getString(KEY_RELAY_TOKEN, DEFAULT_RELAY_TOKEN).orEmpty(),
        relayId = preferences.getString(KEY_RELAY_ID, null).orEmpty(),
        ringId = preferences.getString(KEY_RING_ID, DEFAULT_RING_ID).orEmpty(),
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

    fun saveSendStatus(status: RelaySendStatus) {
        preferences.edit()
            .putLong(KEY_LAST_SEND_AT, status.timestampMillis)
            .putInt(KEY_LAST_SENT, status.sent)
            .putInt(KEY_LAST_ACCEPTED, status.accepted)
            .putInt(KEY_LAST_DUPLICATES, status.duplicates)
            .putInt(KEY_LAST_REJECTED, status.rejected)
            .apply()
    }

    fun loadSendStatus(): RelaySendStatus? {
        val timestamp = preferences.getLong(KEY_LAST_SEND_AT, 0)
        if (timestamp == 0L) return null
        return RelaySendStatus(
            timestampMillis = timestamp,
            sent = preferences.getInt(KEY_LAST_SENT, 0),
            accepted = preferences.getInt(KEY_LAST_ACCEPTED, 0),
            duplicates = preferences.getInt(KEY_LAST_DUPLICATES, 0),
            rejected = preferences.getInt(KEY_LAST_REJECTED, 0),
        )
    }
}

data class RelaySendStatus(
    val timestampMillis: Long,
    val sent: Int,
    val accepted: Int,
    val duplicates: Int,
    val rejected: Int,
)

const val MIN_SYNC_INTERVAL_MINUTES = 1
const val MAX_SYNC_INTERVAL_MINUTES = 60
const val DEFAULT_SYNC_INTERVAL_MINUTES = 10
