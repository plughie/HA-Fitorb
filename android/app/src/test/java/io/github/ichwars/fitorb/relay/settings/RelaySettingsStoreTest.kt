package io.github.ichwars.fitorb.relay.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PREFERENCES_NAME = "fitorb_relay_settings"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RelaySettingsStoreTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun loadReturnsDefaults() {
        val store = RelaySettingsStore(context)

        assertEquals(
            RelaySettings(
                homeAssistantUrl = DEFAULT_HOME_ASSISTANT_URL,
                relayToken = DEFAULT_RELAY_TOKEN,
                relayId = "",
                ringId = DEFAULT_RING_ID,
                syncIntervalMinutes = DEFAULT_SYNC_INTERVAL_MINUTES,
            ),
            store.load(),
        )
    }

    @Test
    fun savePersistsTrimmedSettings() {
        val store = RelaySettingsStore(context)

        store.save(
            RelaySettings(
                homeAssistantUrl = " https://ha.example.net ",
                relayToken = " fitorb_relay_secret ",
                relayId = " pixel-8 ",
                ringId = " AA:BB:CC:DD:EE:FF ",
                ringName = " Mein Ring ",
                syncIntervalMinutes = 15,
                stepGoal = 12_500,
            )
        )

        assertEquals(
            RelaySettings(
                homeAssistantUrl = "https://ha.example.net",
                relayToken = "fitorb_relay_secret",
                relayId = "pixel-8",
                ringId = "AA:BB:CC:DD:EE:FF",
                ringName = "Mein Ring",
                syncIntervalMinutes = 15,
                stepGoal = 12_500,
            ),
            store.load(),
        )
    }

    @Test
    fun syncIntervalIsClampedWhenSavedAndLoaded() {
        val store = RelaySettingsStore(context)

        store.save(
            RelaySettings(
                homeAssistantUrl = "https://ha.example.net",
                relayToken = "fitorb_relay_secret",
                relayId = "pixel-8",
                ringId = "AA:BB:CC:DD:EE:FF",
                syncIntervalMinutes = 90,
                stepGoal = 500_000,
            )
        )

        assertEquals(MAX_SYNC_INTERVAL_MINUTES, store.load().syncIntervalMinutes)
        assertEquals(MAX_STEP_GOAL_STEPS, store.load().stepGoal)
    }

    @Test
    fun sendStatusRoundTrips() {
        val store = RelaySettingsStore(context)
        val status = RelaySendStatus(
            timestampMillis = 1_788_393_600_000,
            sent = 12,
            accepted = 7,
            duplicates = 4,
            rejected = 1,
        )

        store.saveSendStatus(status)

        assertEquals(status, store.loadSendStatus())
    }
}
