package io.github.ichwars.fitorb.relay.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.ichwars.fitorb.relay.FITORB_APP_VERSION
import io.github.ichwars.fitorb.relay.ble.AndroidFitorbBleCollector
import io.github.ichwars.fitorb.relay.data.RelayDatabase
import io.github.ichwars.fitorb.relay.settings.RelaySettingsStore

class RelayWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = RelaySettingsStore(applicationContext)
        val settings = store.load()
        if (!settings.isReadyForWorker()) {
            return Result.success()
        }
        return try {
            RelaySyncRunner(
                dao = RelayDatabase.open(applicationContext).relaySampleDao(),
                collector = AndroidFitorbBleCollector(applicationContext),
                uploader = fitorbRelayUploader(settings.homeAssistantUrl),
                appVersion = FITORB_APP_VERSION,
            ).run(settings)
            Result.success()
        } catch (_: IllegalArgumentException) {
            Result.failure()
        } catch (_: Exception) {
            Result.success()
        } finally {
            RelaySyncScheduler.appendNext(applicationContext, settings.syncIntervalMinutes)
        }
    }
}

private fun io.github.ichwars.fitorb.relay.settings.RelaySettings.isReadyForWorker(): Boolean =
    homeAssistantUrl.isNotBlank() &&
        relayToken.isNotBlank() &&
        relayId.isNotBlank() &&
        ringId.isNotBlank()
