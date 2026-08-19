package com.timebox.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.timebox.android.data.AppSettings
import com.timebox.android.reminders.deliverDueReminders
import com.timebox.android.ui.TimeboxApp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class MainActivity : ComponentActivity() {

    private var notificationsAllowed by mutableStateOf(false)
    private val shownReminderIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsAllowed = canDisplayNotifications() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationsAllowed = canDisplayNotifications()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val app = application as TimeboxApplication
                while (true) {
                    deliverDueReminders(
                        repository = app.repository,
                        notifier = app.reminderNotifier,
                        shownInProcess = shownReminderIds,
                    )
                    delay(60_000)
                }
            }
        }

        setContent {
            val context = LocalContext.current
            val repository = (context.applicationContext as TimeboxApplication).repository
            val scope = rememberCoroutineScope()

            val systemDark = isSystemInDarkTheme()
            val stored by repository.settings.collectAsState(
                initial = AppSettings(baseUrl = "", apiKey = "", darkTheme = null)
            )
            val isDark = stored.darkTheme ?: systemDark

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = useDarkSystemBarIcons(isDark)
                    isAppearanceLightNavigationBars = useDarkSystemBarIcons(isDark)
                }
            }

            TimeboxTheme(darkTheme = isDark) {
                TimeboxApp(
                    isDark = isDark,
                    onToggleDark = { scope.launch { repository.setDarkTheme(!isDark) } },
                    notificationsAllowed = notificationsAllowed,
                    onRequestNotificationPermission = ::requestNotificationPermission,
                    onOpenNotificationSettings = ::openNotificationSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed = canDisplayNotifications()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                data = Uri.parse("package:$packageName")
            },
        )
    }

    private fun canDisplayNotifications(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(this).areNotificationsEnabled()
    }
}

internal fun useDarkSystemBarIcons(isDarkTheme: Boolean): Boolean = !isDarkTheme
