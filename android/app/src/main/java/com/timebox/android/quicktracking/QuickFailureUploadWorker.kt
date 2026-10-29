package com.timebox.android.quicktracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timebox.android.TimeboxApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuickFailureUploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.Main.immediate) {
        try {
            (applicationContext as TimeboxApplication).quickTracking.synchronize()
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
