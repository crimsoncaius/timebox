package com.timebox.android.checkin

import android.content.Context
import androidx.work.*
import com.timebox.android.BuildConfig
import com.timebox.android.TimeboxApplication
import java.util.concurrent.TimeUnit

class CheckInDismissReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (!BuildConfig.ACTIVITY_TRACKING_DEV) return
        val id = intent.getStringExtra(CheckInNotifier.QUESTION) ?: return
        WorkManager.getInstance(context).enqueueUniqueWork("activity-dismiss:$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CheckInDismissWorker>().setInputData(workDataOf(CheckInNotifier.QUESTION to id)).build())
    }
}

class CheckInDismissWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.ACTIVITY_TRACKING_DEV) return Result.success()
        val id = inputData.getString(CheckInNotifier.QUESTION) ?: return Result.failure()
        val repository = (applicationContext as TimeboxApplication).activityRepository
        repository.refresh()
        if (repository.state.value.snapshot?.checkIn?.question?.id == id)
            repository.checkIn(com.timebox.android.data.remote.CheckInEventDto("notification_dismiss", questionId = id))
        return Result.success()
    }
}

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
