package de.adminforge.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class UpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return try {
            UpdateChecker.checkSilently(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
