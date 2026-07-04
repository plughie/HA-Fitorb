package io.github.ichwars.fitorb.relay.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RelayWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
