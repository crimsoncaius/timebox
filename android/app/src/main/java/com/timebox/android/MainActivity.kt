package com.timebox.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.timebox.android.data.AppSettings
import com.timebox.android.ui.TimeboxApp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val repository = (context.applicationContext as TimeboxApplication).repository
            val scope = rememberCoroutineScope()

            val systemDark = isSystemInDarkTheme()
            val stored by repository.settings.collectAsState(
                initial = AppSettings(baseUrl = "", apiKey = "", darkTheme = null)
            )
            val isDark = stored.darkTheme ?: systemDark

            TimeboxTheme(darkTheme = isDark) {
                TimeboxApp(
                    isDark = isDark,
                    onToggleDark = { scope.launch { repository.setDarkTheme(!isDark) } },
                )
            }
        }
    }
}
