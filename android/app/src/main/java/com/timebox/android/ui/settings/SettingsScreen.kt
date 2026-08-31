package com.timebox.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveConnection: () -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenThemePreview: () -> Unit = {},
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
        ConnectionStatus(connected = state.window != null && state.error == null)
        Spacer(Modifier.height(14.dp))

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
            SectionHeader(
                title = "Notifications",
                description = if (notificationsAllowed) {
                    "This device can display Battle Plan reminders. Delivery may be delayed by battery restrictions."
                } else {
                    "Reminders still save to the server, but this device cannot display them until notifications are enabled."
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
private fun ConnectionStatus(connected: Boolean) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .clip(TimeboxShapes.chip)
            .background(colors.low)
            .border(1.dp, colors.hairline, TimeboxShapes.chip)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(if (connected) colors.tertiary else colors.error),
        )
        Text(
            text = if (connected) "Up to date" else "Not connected",
            style = TimeboxTheme.type.label.copy(fontSize = 11.sp),
            color = colors.onVariant,
        )
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
