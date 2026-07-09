package com.cashbk.app.data.sync.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashbk.app.data.di.AppContainer
import java.lang.Exception

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncManager = AppContainer.getSyncManager(applicationContext)
        return try {
            syncManager.syncPendingQueue()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
