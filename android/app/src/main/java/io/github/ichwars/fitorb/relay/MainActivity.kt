package io.github.ichwars.fitorb.relay

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.github.ichwars.fitorb.relay.ble.AndroidFitorbBleCollector
import io.github.ichwars.fitorb.relay.data.RelayDatabase
import io.github.ichwars.fitorb.relay.data.toDto
import io.github.ichwars.fitorb.relay.settings.RelaySettings
import io.github.ichwars.fitorb.relay.settings.RelaySettingsStore
import io.github.ichwars.fitorb.relay.settings.RelaySendStatus
import io.github.ichwars.fitorb.relay.sync.RelaySyncRunner
import io.github.ichwars.fitorb.relay.sync.RelaySyncScheduler
import io.github.ichwars.fitorb.relay.sync.fitorbRelayUploader
import io.github.ichwars.fitorb.relay.ui.FitorbRelayApp

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // The UI reports missing permissions through the next manual or scheduled sync attempt.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        requestRelayPermissions()
        val store = RelaySettingsStore(this)
        val database = RelayDatabase.open(this)
        val defaultRelayId = defaultRelayId()
        val initialSettings = store.load().withDefaultRelayId(defaultRelayId)
        if (initialSettings.isReadyForRelay()) {
            RelaySyncScheduler.ensureNext(this, initialSettings.syncIntervalMinutes)
        }

        setContent {
            FitorbRelayApp(
                initialSettings = initialSettings,
                defaultRelayId = defaultRelayId,
                appVersion = FITORB_APP_VERSION,
                onSave = { settings ->
                    val normalized = settings.withDefaultRelayId(defaultRelayId)
                    store.save(normalized)
                    if (normalized.isReadyForRelay()) {
                        RelaySyncScheduler.replaceNext(this, normalized.syncIntervalMinutes)
                    }
                },
                onUpload = { settings ->
                    val normalized = settings.withDefaultRelayId(defaultRelayId)
                    store.save(normalized)
                    RelaySyncRunner(
                        dao = database.relaySampleDao(),
                        collector = AndroidFitorbBleCollector(this),
                        uploader = fitorbRelayUploader(normalized.homeAssistantUrl),
                        appVersion = FITORB_APP_VERSION,
                    ).run(normalized).also { result ->
                        store.saveSendStatus(result.toSendStatus())
                        RelaySyncScheduler.replaceNext(this, normalized.syncIntervalMinutes)
                    }
                },
                onLoadSendStatus = store::loadSendStatus,
                onLoadSamples = { settings ->
                    val ringId = settings.ringId.trim()
                    if (ringId.isBlank()) {
                        emptyList()
                    } else {
                        database.relaySampleDao()
                            .latestSamplesForRing(ringId, limit = 200)
                            .map { it.toDto() }
                    }
                },
            )
        }
    }

    private fun requestRelayPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

private fun io.github.ichwars.fitorb.relay.sync.RelaySyncResult.toSendStatus() = RelaySendStatus(
    timestampMillis = System.currentTimeMillis(),
    sent = uploadedSamples.size,
    accepted = ack.accepted.size,
    duplicates = ack.duplicates.size,
    rejected = ack.rejected.size,
)

private fun ComponentActivity.configureSystemBars() {
    val barColor = Color.parseColor("#040707")
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(barColor),
        navigationBarStyle = SystemBarStyle.dark(barColor),
    )
}

private fun RelaySettings.withDefaultRelayId(defaultRelayId: String): RelaySettings =
    if (relayId.isBlank()) copy(relayId = defaultRelayId) else this

private fun RelaySettings.isReadyForRelay(): Boolean =
    homeAssistantUrl.isNotBlank() &&
        relayToken.isNotBlank() &&
        relayId.isNotBlank() &&
        ringId.isNotBlank()
