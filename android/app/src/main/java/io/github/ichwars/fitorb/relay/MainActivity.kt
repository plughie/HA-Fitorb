package io.github.ichwars.fitorb.relay

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import io.github.ichwars.fitorb.relay.network.FitorbRelayApi
import io.github.ichwars.fitorb.relay.settings.RelaySettings
import io.github.ichwars.fitorb.relay.settings.RelaySettingsStore
import io.github.ichwars.fitorb.relay.ui.FitorbRelayApp
import java.time.Instant
import java.util.Locale

private const val APP_VERSION = "0.1.0"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = RelaySettingsStore(this)
        val defaultRelayId = defaultRelayId()
        val initialSettings = store.load().withDefaultRelayId(defaultRelayId)

        setContent {
            FitorbRelayApp(
                initialSettings = initialSettings,
                defaultRelayId = defaultRelayId,
                onSave = store::save,
                onUpload = { settings ->
                    FitorbRelayApi(settings.homeAssistantUrl)
                        .upload(createManualBatch(settings), settings.relayToken)
                },
            )
        }
    }
}

private fun RelaySettings.withDefaultRelayId(defaultRelayId: String): RelaySettings =
    if (relayId.isBlank()) copy(relayId = defaultRelayId) else this

private fun defaultRelayId(): String = "android-${Build.MODEL}".trim()

private fun createManualBatch(settings: RelaySettings): RelayBatchDto {
    val now = Instant.now()
    val ringId = settings.ringId.trim()
    val relayId = settings.relayId.trim().ifBlank { defaultRelayId() }
    return RelayBatchDto(
        relayId = relayId,
        ringId = ringId,
        appVersion = APP_VERSION,
        protocolVersion = 1,
        sentAt = now.toString(),
        samples = listOf(
            RelaySampleDto(
                sampleId = "${sampleIdPrefix(relayId)}-manual-${now.toEpochMilli()}",
                ringId = ringId,
                metric = "heart_rate",
                timestamp = now.toString(),
                value = RelaySampleValue.IntValue(72),
                unit = "bpm",
                capturedAt = now.toString(),
            ),
        ),
        backlog = 0,
    )
}

private fun sampleIdPrefix(relayId: String): String =
    relayId
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]"), "-")
        .trim('-')
        .ifBlank { "android-relay" }
        .take(48)
