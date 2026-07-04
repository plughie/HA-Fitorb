package io.github.ichwars.fitorb.relay

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import io.github.ichwars.fitorb.relay.network.FitorbRelayApi
import io.github.ichwars.fitorb.relay.network.RelayUploadException
import io.github.ichwars.fitorb.relay.settings.MAX_SYNC_INTERVAL_MINUTES
import io.github.ichwars.fitorb.relay.settings.MIN_SYNC_INTERVAL_MINUTES
import io.github.ichwars.fitorb.relay.settings.RelaySettings
import io.github.ichwars.fitorb.relay.settings.RelaySettingsStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

private const val APP_VERSION = "0.1.0"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = RelaySettingsStore(this)
        val initialSettings = store.load().withDefaultRelayId(defaultRelayId())

        setContent {
            FitorbRelayScreen(
                initialSettings = initialSettings,
                onSave = store::save,
                onUpload = { settings ->
                    FitorbRelayApi(settings.homeAssistantUrl)
                        .upload(createManualBatch(settings), settings.relayToken)
                },
            )
        }
    }
}

@Composable
private fun FitorbRelayScreen(
    initialSettings: RelaySettings,
    onSave: (RelaySettings) -> Unit,
    onUpload: suspend (RelaySettings) -> io.github.ichwars.fitorb.relay.data.RelayAckDto,
) {
    var homeAssistantUrl by rememberSaveable {
        mutableStateOf(initialSettings.homeAssistantUrl)
    }
    var relayToken by rememberSaveable { mutableStateOf(initialSettings.relayToken) }
    var relayId by rememberSaveable { mutableStateOf(initialSettings.relayId) }
    var ringId by rememberSaveable { mutableStateOf(initialSettings.ringId) }
    var syncInterval by rememberSaveable {
        mutableStateOf(
            initialSettings.syncIntervalMinutes.coerceIn(
                MIN_SYNC_INTERVAL_MINUTES,
                MAX_SYNC_INTERVAL_MINUTES,
            ),
        )
    }
    var status by rememberSaveable { mutableStateOf("Bereit") }
    var uploading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun currentSettings() = RelaySettings(
        homeAssistantUrl = homeAssistantUrl,
        relayToken = relayToken,
        relayId = relayId,
        ringId = ringId,
        syncIntervalMinutes = syncInterval,
    )

    val trimmedUrl = homeAssistantUrl.trim()
    val trimmedToken = relayToken.trim()
    val trimmedRelayId = relayId.trim()
    val trimmedRingId = ringId.trim()
    val canUpload = !uploading &&
        trimmedUrl.startsWith("https://", ignoreCase = true) &&
        trimmedToken.startsWith("fitorb_relay_") &&
        trimmedRelayId.isNotEmpty() &&
        trimmedRingId.isNotEmpty()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Fitorb Relay",
                    style = MaterialTheme.typography.headlineMedium,
                )
                OutlinedTextField(
                    value = homeAssistantUrl,
                    onValueChange = { homeAssistantUrl = it },
                    label = { Text("Home Assistant URL") },
                    placeholder = { Text("https://ha.example.net") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = relayToken,
                    onValueChange = { relayToken = it },
                    label = { Text("Relay Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = relayId,
                    onValueChange = { relayId = it },
                    label = { Text("Relay ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ringId,
                    onValueChange = { ringId = it },
                    label = { Text("Ring ID") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Intervall: $syncInterval min",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = syncInterval.toFloat(),
                    onValueChange = { value ->
                        syncInterval = value.toInt().coerceIn(
                            MIN_SYNC_INTERVAL_MINUTES,
                            MAX_SYNC_INTERVAL_MINUTES,
                        )
                    },
                    valueRange = MIN_SYNC_INTERVAL_MINUTES.toFloat()..
                        MAX_SYNC_INTERVAL_MINUTES.toFloat(),
                    steps = MAX_SYNC_INTERVAL_MINUTES - MIN_SYNC_INTERVAL_MINUTES - 1,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            onSave(currentSettings())
                            status = "Gespeichert"
                        },
                    ) {
                        Text("Speichern")
                    }
                    Button(
                        enabled = canUpload,
                        onClick = {
                            val settings = currentSettings()
                            onSave(settings)
                            uploading = true
                            status = "Sende Test..."
                            scope.launch {
                                status = try {
                                    val ack = onUpload(settings)
                                    "OK: ${ack.accepted.size} neu, " +
                                        "${ack.duplicates.size} doppelt, " +
                                        "${ack.rejected.size} abgewiesen"
                                } catch (exception: RelayUploadException) {
                                    "Fehler: ${exception.message.orEmpty()}"
                                } catch (exception: IllegalArgumentException) {
                                    "Fehler: ${exception.message.orEmpty()}"
                                } finally {
                                    uploading = false
                                }
                            }
                        },
                    ) {
                        Text("Test senden")
                    }
                    if (uploading) {
                        CircularProgressIndicator()
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
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
