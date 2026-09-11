package com.timebox.android.checkin

import android.content.Context
import androidx.work.*
import com.timebox.android.BuildConfig
import com.timebox.android.TimeboxApplication
import java.util.concurrent.TimeUnit

class CheckInWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (BuildConfig.ACTIVITY_TRACKING_DEV) (applicationContext as TimeboxApplication).checkIns.tick()
        return Result.success()
    }
    companion object {
        fun schedule(context: Context) {
            if (BuildConfig.ACTIVITY_TRACKING_DEV) WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "activity-check-ins", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CheckInWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
