package com.timebox.android.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.timebox.android.reminders.DailyReminder
import com.timebox.android.reminders.PlannedBlockReminderSettings
import com.timebox.android.reminders.plannedBlockLeadLabel
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.components.SettingRow
import com.timebox.android.ui.components.TimeboxSwitch
import com.timebox.android.ui.day.CheckInSettingRows
import com.timebox.android.ui.focus.FocusWakeSettingRow
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    isDark: Boolean,
    onToggleDark: () -> Unit,
    onStartHourDelta: (Int) -> Unit,
    onEndHourDelta: (Int) -> Unit,
    onToggleFullDay: () -> Unit,
    onDailyReminderChange: (Boolean, Boolean?, LocalTime?) -> Unit = { _, _, _ -> },
    onPlannedBlockRemindersChange: (PlannedBlockReminderSettings) -> Unit = {},
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {},
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = TimeboxDimens.bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsGroup(
            title = "Day & time",
            scope = "All devices",
            description = "How the timeline is framed and how daily totals are grouped.",
        ) {
            val window = state.window
            when {
                state.loading && window == null -> SettingRow(title = "Day window", description = "Loading…") {}
                window == null -> SettingRow(title = "Day window", description = state.error ?: "Could not load settings.") {
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
                else -> {
                    val hoursAlpha = if (window.showFullDay) 0.5f else 1f
                    Box(Modifier.alpha(hoursAlpha)) {
                        SettingRow(title = "Start hour", description = "First hour shown on the timeline (0–23).") {
                            Stepper(
                                value = window.startHour,
                                enabled = !window.showFullDay,
                                onDecrease = { onStartHourDelta(-1) },
                                onIncrease = { onStartHourDelta(1) },
                                decreaseLabel = "Decrease start hour",
                                increaseLabel = "Increase start hour",
                            )
                        }
                    }
                    Box(Modifier.alpha(hoursAlpha)) {
                        SettingRow(title = "End hour", description = "Exclusive end (1–24). 8–20 shows 8:00 through 19:59.") {
                            Stepper(
                                value = window.endHour,
                                enabled = !window.showFullDay,
                                onDecrease = { onEndHourDelta(-1) },
                                onIncrease = { onEndHourDelta(1) },
                                decreaseLabel = "Decrease end hour",
                                increaseLabel = "Increase end hour",
                            )
                        }
                    }
                    SettingRow(title = "Show full 24 hours", description = "Ignore the start and end hours and show the whole day.") {
                        TimeboxSwitch(checked = window.showFullDay, onCheckedChange = { onToggleFullDay() })
                    }
                }
            }
            ReportingZoneRow(
                current = state.timezone,
                saving = state.saving,
                onSave = { zone ->
                    onReportingZoneChange(zone)
                    onSaveReportingZone()
                },
            )
        }

        SettingsGroup(title = "Focus & check-ins", scope = "This device") {
            FocusWakeSettingRow()
            CheckInSettingRows()
        }

        SettingsGroup(
            title = "Reminders",
            scope = "This device",
            description = if (notificationsAllowed) {
                "Delivery may be delayed by battery restrictions."
            } else {
                "Battle Plan reminders still save to the server and Daily Reminder preferences stay saved on this device, " +
                    "but this device cannot display any reminders until notifications are enabled."
            },
        ) {
            SettingRow(
                title = "Notifications",
                description = if (notificationsAllowed) "Allowed on this device" else "Off on this device",
            ) {
                if (notificationsAllowed) {
                    TextButton(onClick = onOpenNotificationSettings) { Text("Manage") }
                } else {
                    TextButton(onClick = onRequestNotificationPermission) { Text("Enable") }
                }
            }
            if (!notificationsAllowed) {
                TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open notification settings")
                }
            }
            DailyReminderRow("Day planning reminder", state.dailyReminders.planning, true, onDailyReminderChange)
            DailyReminderRow("Day review reminder", state.dailyReminders.review, false, onDailyReminderChange)
            PlannedBlockReminderRows(
                settings = state.plannedBlockReminders,
                exactAlarmsAllowed = exactAlarmsAllowed,
                onChange = onPlannedBlockRemindersChange,
                onRequestExactAlarms = onRequestExactAlarms,
            )
        }

        SettingsGroup(title = "Appearance", scope = "This device") {
            SettingRow(title = "Dark theme", description = "Charcoal surfaces, brighter lanes.") {
                TimeboxSwitch(checked = isDark, onCheckedChange = { onToggleDark() })
            }
        }

        SettingsGroup(
            title = "Advanced",
            scope = "This device",
            description = "Where this device finds the Timebox API. The key is only needed when the server sets API_KEY.",
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConnectionField(
                    value = state.baseUrlInput,
                    onValueChange = onBaseUrlChange,
                    placeholder = "http://10.0.2.2:8000",
                    label = "Server address",
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
            SettingRow(title = "Theme preview", description = "Inspect surfaces, type, controls, and states.") {
                TextButton(onClick = onOpenThemePreview) { Text("Open") }
            }
        }
    }
}

/** A titled card of related settings, labelled with where its values live. */
@Composable
private fun SettingsGroup(
    title: String,
    scope: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TimeboxTheme.colors
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = TimeboxTheme.type.sectionTitle,
                    color = colors.on,
                    modifier = Modifier.semantics { heading() },
                )
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(description, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
            }
            Text(
                text = scope.uppercase(),
                style = TimeboxTheme.type.laneLabel.copy(fontSize = 9.5.sp),
                color = colors.onVariant,
                maxLines = 1,
                modifier = Modifier
                    .border(1.dp, colors.hairline, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

private val ReportingZones: List<String> by lazy {
    val regions = listOf("Africa/", "America/", "Antarctica/", "Arctic/", "Asia/", "Atlantic/", "Australia/", "Europe/", "Indian/", "Pacific/")
    listOf("UTC") + ZoneId.getAvailableZoneIds().filter { id -> regions.any { id.startsWith(it) } }.sorted()
}

@Composable
private fun ReportingZoneRow(current: String?, saving: Boolean, onSave: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    SettingRow(
        title = "Reporting Time Zone",
        description = "${current ?: "Not set"}. Daily totals are grouped by this zone; travel does not change it.",
    ) {
        TextButton(enabled = !saving, onClick = { choosing = true }) { Text("Change") }
    }
    if (choosing) {
        ReportingZoneDialog(
            current = current,
            onDismiss = { choosing = false },
            onSave = { zone ->
                choosing = false
                onSave(zone)
            },
        )
    }
}

@Composable
private fun ReportingZoneDialog(current: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(current) }
    val matches = remember(query) {
        val needle = query.trim().replace(' ', '_')
        if (needle.isEmpty()) ReportingZones else ReportingZones.filter { it.contains(needle, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(TimeboxShapes.group)
                .background(colors.card)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Reporting Time Zone", style = TimeboxTheme.type.sectionTitle, color = colors.on)
            Text(
                "Shared by all devices. Changing it regroups daily totals; recorded times and elapsed duration stay the same.",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
            )
            ConnectionField(value = query, onValueChange = { query = it }, placeholder = "Singapore", label = "Search time zones")
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                if (matches.isEmpty()) {
                    item {
                        Text(
                            "No matching time zones",
                            style = TimeboxTheme.type.bodySmall,
                            color = colors.onVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                items(matches, key = { it }) { zone ->
                    val isSelected = zone == selected
                    Text(
                        text = zone.replace('_', ' '),
                        style = TimeboxTheme.type.body,
                        color = if (isSelected) colors.onSelected else colors.on,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TimeboxShapes.field)
                            .background(if (isSelected) colors.selected else Color.Transparent)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { selected = zone })
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    enabled = selected != null && selected != current,
                    onClick = { selected?.let(onSave) },
                ) { Text("Save time zone") }
            }
        }
    }
}

@Composable
private fun PlannedBlockReminderRows(
    settings: PlannedBlockReminderSettings,
    exactAlarmsAllowed: Boolean,
    onChange: (PlannedBlockReminderSettings) -> Unit,
    onRequestExactAlarms: () -> Unit,
) {
    var choosingLead by remember { mutableStateOf(false) }
    SettingRow(
        title = "Remind me before Planned Blocks",
        description = if (settings.enabled) plannedBlockLeadLabel(settings.leadMinutes) else "Off",
    ) {
        TimeboxSwitch(checked = settings.enabled, onCheckedChange = { onChange(settings.copy(enabled = it)) })
    }
    Box(Modifier.alpha(if (settings.enabled) 1f else 0.5f)) { SettingRow(title = "When", description = "Applies to every Planned Block") {
        Box {
            TextButton(enabled = settings.enabled, onClick = { choosingLead = true }) {
                Text(plannedBlockLeadLabel(settings.leadMinutes))
            }
            DropdownMenu(expanded = choosingLead, onDismissRequest = { choosingLead = false }) {
                PlannedBlockReminderSettings.LeadOptions.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(plannedBlockLeadLabel(minutes)) },
                        onClick = {
                            choosingLead = false
                            onChange(settings.copy(leadMinutes = minutes))
                        },
                    )
                }
            }
        }
    } }
    if (settings.enabled && !exactAlarmsAllowed) {
        SettingRow(title = "Reminders may arrive a few minutes late", description = "Exact alarms are not allowed for Timebox") {
            TextButton(onClick = onRequestExactAlarms) { Text("Allow exact alarms") }
        }
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
    enabled: Boolean = true,
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
            enabled = enabled,
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
            enabled = enabled,
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
