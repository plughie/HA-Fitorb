package io.github.ichwars.fitorb.relay.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val RELAY_SYNC_WORK_NAME = "fitorb_relay_sync"

object RelaySyncScheduler {
    fun runNow(context: Context) {
        enqueue(context, delayMinutes = 0, policy = ExistingWorkPolicy.REPLACE)
    }

    fun replaceNext(context: Context, delayMinutes: Int) {
        enqueue(context, delayMinutes = delayMinutes, policy = ExistingWorkPolicy.REPLACE)
    }

    fun ensureNext(context: Context, delayMinutes: Int) {
        enqueue(context, delayMinutes = delayMinutes, policy = ExistingWorkPolicy.KEEP)
    }

    fun appendNext(context: Context, delayMinutes: Int) {
        enqueue(context, delayMinutes = delayMinutes, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(
        context: Context,
        delayMinutes: Int,
        policy: ExistingWorkPolicy,
    ) {
        val delay = delayMinutes.coerceIn(0, 60)
        val request = OneTimeWorkRequestBuilder<RelayWorker>()
            .setInitialDelay(delay.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(RELAY_SYNC_WORK_NAME, policy, request)
    }
}
