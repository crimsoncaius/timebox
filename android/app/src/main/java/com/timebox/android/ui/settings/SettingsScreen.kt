package com.timebox.android.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.timebox.android.reminders.DailyReminder
import java.time.LocalTime
import androidx.compose.foundation.text.KeyboardOptions
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.components.SectionHeader
import com.timebox.android.ui.components.SettingRow
import com.timebox.android.ui.components.TimeboxSwitch
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    isDark: Boolean,
    onToggleDark: () -> Unit,
    onStartHourDelta: (Int) -> Unit,
    onEndHourDelta: (Int) -> Unit,
    onToggleFullDay: () -> Unit,
    onDailyReminderChange: (Boolean, Boolean?, LocalTime?) -> Unit = { _, _, _ -> },
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveConnection: () -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenThemePreview: () -> Unit = {},
    onReportingZoneChange: (String) -> Unit = {},
    onSaveReportingZone: () -> Unit = {},
    onRetry: () -> Unit,
) {
    val colors = TimeboxTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = TimeboxDimens.bottomInset),
    ) {
        if (com.timebox.android.BuildConfig.ACTIVITY_TRACKING_DEV) SectionCard {
            com.timebox.android.ui.focus.FocusWakeSettings()
            com.timebox.android.ui.day.CheckInSettings()
            SectionHeader(title = "Reporting Time Zone", description = "Shared by all devices. Changes recalculate daily shares without changing recorded times or elapsed duration. Travel does not change it.")
            OutlinedTextField(value = state.reportingZoneInput, onValueChange = onReportingZoneChange, label = { Text("Reporting Time Zone") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onSaveReportingZone, enabled = !state.saving && state.reportingZoneInput.isNotBlank() && state.reportingZoneInput != state.timezone) { Text("Save time zone") }
        }
        val window = state.window
        when {
            state.loading && window == null -> {
                Box(Modifier.fillMaxWidth().height(220.dp)) { LoadingState() }
            }
            window == null -> {
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    ErrorState(
                        message = state.error ?: "Could not load settings.",
                        onRetry = onRetry,
                    )
                }
            }
            else -> {
                SectionCard {
                    SectionHeader(
                        title = "Day window",
                        description = "Visible hours on the timeline. End hour is exclusive " +
                            "(e.g. 8–20 shows 8:00 through 19:59).",
                    )
                    Column(
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingRow(
                            title = "Start hour",
                            description = "First hour shown (0–23).",
                        ) {
                            Stepper(
                                value = window.startHour,
                                onDecrease = { onStartHourDelta(-1) },
                                onIncrease = { onStartHourDelta(1) },
                                decreaseLabel = "Decrease start hour",
                                increaseLabel = "Increase start hour",
                            )
                        }
                        SettingRow(
                            title = "End hour",
                            description = "Exclusive end (1–24).",
                        ) {
                            Stepper(
                                value = window.endHour,
                                onDecrease = { onEndHourDelta(-1) },
                                onIncrease = { onEndHourDelta(1) },
                                decreaseLabel = "Decrease end hour",
                                increaseLabel = "Increase end hour",
                            )
                        }
                        SettingRow(
                            title = "Show full 24 hours",
                            description = "Ignore start/end and display the full day.",
                        ) {
                            TimeboxSwitch(
                                checked = window.showFullDay,
                                onCheckedChange = { onToggleFullDay() },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        SectionCard {
            SectionHeader(title = "Appearance")
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingRow(
                    title = "Dark theme",
                    description = "Charcoal surfaces, brighter lanes.",
                ) {
                    TimeboxSwitch(checked = isDark, onCheckedChange = { onToggleDark() })
                }
                SettingRow(
                    title = "Theme preview",
                    description = "Inspect surfaces, type, controls, and states.",
                ) {
                    androidx.compose.material3.TextButton(onClick = onOpenThemePreview) { Text("Open") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard {
            SectionHeader(title = "Daily reminders", description = "Optional prompts that stay on this device.")
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DailyReminderRow("Day planning reminder", state.dailyReminders.planning, true, onDailyReminderChange)
                DailyReminderRow("Day review reminder", state.dailyReminders.review, false, onDailyReminderChange)
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard {
            SectionHeader(
                title = "Notifications",
                description = if (notificationsAllowed) {
                    "This device can display Battle Plan and Daily Reminders. Delivery may be delayed by battery restrictions."
                } else {
                    "Battle Plan reminders still save to the server. Daily Reminder preferences stay saved on this device, but this device cannot display either until notifications are enabled."
                },
            )
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!notificationsAllowed) {
                    PrimaryButton(
                        text = "Enable notifications",
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                androidx.compose.material3.TextButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open notification settings")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard {
            SectionHeader(
                title = "Server",
                description = "Where this device finds the Timebox API. The key is only " +
                    "needed when the server sets API_KEY.",
            )
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConnectionField(
                    value = state.baseUrlInput,
                    onValueChange = onBaseUrlChange,
                    placeholder = "http://10.0.2.2:8000",
                    label = "Address",
                )
                ConnectionField(
                    value = state.apiKeyInput,
                    onValueChange = onApiKeyChange,
                    placeholder = "Optional",
                    label = "API key",
                )
                PrimaryButton(
                    text = "Save connection",
                    onClick = onSaveConnection,
                    enabled = state.connectionDirty,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = state.timezone?.let { "Timezone $it" } ?: "Timezone unknown",
            style = TimeboxTheme.type.mono.copy(fontSize = 11.sp),
            color = colors.outlineVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DailyReminderRow(
    title: String,
    reminder: DailyReminder,
    planning: Boolean,
    onChange: (Boolean, Boolean?, LocalTime?) -> Unit,
) {
    val context = LocalContext.current
    SettingRow(title = title, description = "${if (reminder.enabled) "Daily at" else "Off — saved time"} ${reminder.time}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = reminder.enabled,
                onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        onChange(planning, null, LocalTime.of(hour, minute))
                    }, reminder.time.hour, reminder.time.minute, false).show()
                },
            ) { Text(reminder.time.toString()) }
            TimeboxSwitch(checked = reminder.enabled, onCheckedChange = { onChange(planning, it, null) })
        }
    }
}

@Composable
private fun Stepper(
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
) {
    val colors = TimeboxTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RoundIconButton(
            icon = Icons.Outlined.Remove,
            contentDescription = decreaseLabel,
            onClick = onDecrease,
            tint = colors.on,
            diameter = 36.dp,
            background = colors.surf,
            iconSize = 18.dp,
        )
        Text(
            text = value.toString(),
            style = TimeboxTheme.type.mono.copy(fontSize = 15.sp),
            color = colors.on,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(42.dp),
        )
        RoundIconButton(
            icon = Icons.Outlined.Add,
            contentDescription = increaseLabel,
            onClick = onIncrease,
            tint = colors.on,
            diameter = 36.dp,
            background = colors.surf,
            iconSize = 18.dp,
        )
    }
}

@Composable
private fun ConnectionField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
) {
    val colors = TimeboxTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = {
            Text(label, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        },
        placeholder = {
            Text(placeholder, style = TimeboxTheme.type.body, color = colors.outlineVariant)
        },
        textStyle = TimeboxTheme.type.body.copy(color = colors.on),
        shape = TimeboxShapes.field,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.field,
            unfocusedContainerColor = colors.field,
            focusedIndicatorColor = colors.outline,
            unfocusedIndicatorColor = colors.hairline,
            cursorColor = colors.on,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
