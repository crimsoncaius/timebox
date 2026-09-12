package com.timebox.android.checkin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.timebox.android.MainActivity
import com.timebox.android.R

class CheckInNotifier(private val context: Context) : CheckInNotificationSink {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { manager.createNotificationChannel(NotificationChannel(CHANNEL, "Activity check-ins", NotificationManager.IMPORTANCE_DEFAULT)) }
    override fun allowed() = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun cancel() = manager.cancel(ID)
    override fun show(question: String) {
        if (!allowed()) return
        val intent = Intent(context, MainActivity::class.java).putExtra(QUESTION, question)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismissal = PendingIntent.getBroadcast(context, ID,
            Intent(context, CheckInDismissReceiver::class.java).putExtra(QUESTION, question),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_reminder).setContentTitle("Still doing this?")
            .setContentText("Review your Current Activity. Recording continues.")
            .setContentIntent(pending).setDeleteIntent(dismissal).setAutoCancel(true).setOnlyAlertOnce(true).build())
    }
    companion object {
        const val QUESTION = "activity_check_in_question"
        private const val CHANNEL = "activity-check-ins"
        private const val ID = 15501
    }
}
