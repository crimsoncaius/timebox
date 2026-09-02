package com.timebox.android.ui.day.prototype

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PROTOTYPE — throwaway code.
 *
 * Three variants of the Today action/status distinction, switchable via
 * timebox://prototype/today-affordance?variant=A in a debug build.
 */
class TodayAffordancePrototypeActivity : ComponentActivity() {
    private var activeVariant by mutableStateOf(TodayVariant.A)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        activeVariant = TodayVariant.from(intent.data)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            TimeboxTheme(darkTheme = false) {
                TodayAffordancePrototype(
                    variant = activeVariant,
                    onVariantChange = ::showVariant,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeVariant = TodayVariant.from(intent.data)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            showVariant(activeVariant.previous())
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            showVariant(activeVariant.next())
            true
        }
        else -> super.onKeyUp(keyCode, event)
    }

    private fun showVariant(variant: TodayVariant) {
        activeVariant = variant
        setIntent(
            Intent(intent).setData(
                Uri.parse("timebox://prototype/today-affordance?variant=${variant.key}"),
            ),
        )
    }
}

private enum class TodayVariant(val key: String, val label: String) {
    A("A", "Two-state control"),
    B("B", "Contextual action"),
    C("C", "Explicit status row");

    fun previous(): TodayVariant = entries[(ordinal - 1 + entries.size) % entries.size]
    fun next(): TodayVariant = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(uri: Uri?): TodayVariant {
            val requested = uri?.getQueryParameter("variant")?.uppercase(Locale.ENGLISH)
            return entries.firstOrNull { it.key == requested } ?: A
        }
    }
}

private val prototypeToday: LocalDate = LocalDate.of(2026, 9, 2)
private val initialDate: LocalDate = prototypeToday.minusDays(1)
private val titleDateFormatter = DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.ENGLISH)
private val shortDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

@Composable
private fun TodayAffordancePrototype(
    variant: TodayVariant,
    onVariantChange: (TodayVariant) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var selectedDate by rememberSaveable { mutableStateOf(initialDate) }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 96.dp),
        ) {
            when (variant) {
                TodayVariant.A -> TwoStateHeader(
                    selectedDate = selectedDate,
                    onNavigateToday = { selectedDate = prototypeToday },
                )
                TodayVariant.B -> ContextualHeader(
                    selectedDate = selectedDate,
                    onNavigateToday = { selectedDate = prototypeToday },
                )
                TodayVariant.C -> StatusRowHeader(
                    selectedDate = selectedDate,
                    onNavigateToday = { selectedDate = prototypeToday },
                )
            }

            WeekStrip(
                selectedDate = selectedDate,
                onSelectDate = { selectedDate = it },
            )
            Spacer(Modifier.height(22.dp))
            TimelinePreview()
        }

        PrototypeSwitcher(
            variant = variant,
            selectedDate = selectedDate,
            onPrevious = { onVariantChange(variant.previous()) },
            onNext = { onVariantChange(variant.next()) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TwoStateHeader(selectedDate: LocalDate, onNavigateToday: () -> Unit) {
    HeaderShell {
        DateTitle(selectedDate)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TwoStateTodayControl(
                isToday = selectedDate == prototypeToday,
                onNavigateToday = onNavigateToday,
            )
            Spacer(Modifier.weight(1f))
            WeekMonthControl()
        }
    }
}

@Composable
private fun TwoStateTodayControl(isToday: Boolean, onNavigateToday: () -> Unit) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    val modifier = Modifier
        .height(42.dp)
        .clip(shape)
        .then(
            if (isToday) {
                Modifier
                    .background(colors.plannedSurface)
                    .border(1.dp, colors.plannedBorder, shape)
                    .semantics { contentDescription = "Viewing today" }
            } else {
                Modifier
                    .background(colors.planned)
                    .clickable(role = Role.Button, onClick = onNavigateToday)
                    .semantics { contentDescription = "Go to today" }
            },
        )
        .padding(horizontal = 14.dp)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = if (isToday) Icons.Outlined.Check else Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = if (isToday) colors.planned else colors.lowest,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = if (isToday) "Today" else "Go to today",
            style = TimeboxTheme.type.label,
            color = if (isToday) colors.on else colors.lowest,
        )
    }
}

@Composable
private fun ContextualHeader(selectedDate: LocalDate, onNavigateToday: () -> Unit) {
    val colors = TimeboxTheme.colors
    val isToday = selectedDate == prototypeToday
    HeaderShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isToday) "TODAY" else "DAY",
                    style = TimeboxTheme.type.navLabel,
                    color = if (isToday) colors.planned else colors.onVariant,
                )
                Text(
                    text = selectedDate.format(titleDateFormatter),
                    style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, lineHeight = 27.sp),
                    color = colors.on,
                )
            }
            if (!isToday) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .border(1.dp, colors.planned, RoundedCornerShape(percent = 50))
                        .clickable(role = Role.Button, onClick = onNavigateToday)
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = colors.planned,
                        modifier = Modifier.size(17.dp),
                    )
                    Text("Go to today", style = TimeboxTheme.type.label, color = colors.planned)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Spacer(Modifier.weight(1f))
            WeekMonthControl()
        }
    }
}

@Composable
private fun StatusRowHeader(selectedDate: LocalDate, onNavigateToday: () -> Unit) {
    val colors = TimeboxTheme.colors
    val isToday = selectedDate == prototypeToday
    HeaderShell {
        DateTitle(selectedDate)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (isToday) colors.plannedSurface else colors.low)
                .then(
                    if (isToday) Modifier.border(1.dp, colors.plannedBorder, RoundedCornerShape(13.dp))
                    else Modifier,
                )
                .padding(start = 13.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isToday) Icons.Outlined.Check else Icons.Outlined.CalendarToday,
                contentDescription = null,
                tint = if (isToday) colors.planned else colors.onVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isToday) "You’re on today" else "Viewing ${selectedDate.format(shortDateFormatter)}",
                    style = TimeboxTheme.type.label,
                    color = colors.on,
                )
                Text(
                    text = if (isToday) "Current day" else "Not the current day",
                    style = TimeboxTheme.type.navLabel,
                    color = colors.onVariant,
                )
            }
            if (!isToday) {
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.planned)
                        .clickable(role = Role.Button, onClick = onNavigateToday)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Go to today", style = TimeboxTheme.type.label, color = colors.lowest)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Spacer(Modifier.weight(1f))
            WeekMonthControl()
        }
    }
}

@Composable
private fun HeaderShell(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp),
        content = content,
    )
}

@Composable
private fun DateTitle(selectedDate: LocalDate) {
    val colors = TimeboxTheme.colors
    Text("DAY", style = TimeboxTheme.type.navLabel, color = colors.onVariant)
    Text(
        text = selectedDate.format(titleDateFormatter),
        style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, lineHeight = 27.sp),
        color = colors.on,
    )
}

@Composable
private fun WeekMonthControl() {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .height(32.dp)
            .width(116.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.low)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.lowest),
            contentAlignment = Alignment.Center,
        ) {
            Text("Week", style = TimeboxTheme.type.navLabel, color = colors.on)
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text("Month", style = TimeboxTheme.type.navLabel, color = colors.onVariant)
        }
    }
}

@Composable
private fun WeekStrip(selectedDate: LocalDate, onSelectDate: (LocalDate) -> Unit) {
    val colors = TimeboxTheme.colors
    val monday = prototypeToday.minusDays((prototypeToday.dayOfWeek.value - 1).toLong())
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(7) { offset ->
            val date = monday.plusDays(offset.toLong())
            val selected = date == selectedDate
            val isToday = date == prototypeToday
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .then(
                        if (isToday && !selected) Modifier.border(1.dp, colors.plannedBorder, shape)
                        else Modifier,
                    )
                    .clip(shape)
                    .background(
                        when {
                            selected -> colors.planned
                            isToday -> colors.plannedSurface
                            else -> Color.Transparent
                        },
                    )
                    .clickable { onSelectDate(date) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    date.dayOfWeek.name.take(3),
                    style = TimeboxTheme.type.navLabel,
                    color = if (selected) colors.lowest else colors.onVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    date.dayOfMonth.toString(),
                    style = TimeboxTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        selected -> colors.lowest
                        isToday -> colors.planned
                        else -> colors.on
                    },
                )
            }
        }
    }
}

@Composable
private fun TimelinePreview() {
    val colors = TimeboxTheme.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TimeboxDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
    ) {
        Spacer(Modifier.width(TimeboxDimens.gutterWidth))
        Text("PLANNED", style = TimeboxTheme.type.laneLabel, color = colors.planned, modifier = Modifier.weight(1f))
        Text("ACTUAL", style = TimeboxTheme.type.laneLabel, color = colors.actual, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp)
            .padding(horizontal = TimeboxDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
    ) {
        Column(Modifier.width(TimeboxDimens.gutterWidth), horizontalAlignment = Alignment.End) {
            listOf("8 AM", "9 AM", "10 AM", "11 AM").forEach { label ->
                Text(
                    label,
                    style = TimeboxTheme.type.gutter,
                    color = colors.timelineLabel,
                    modifier = Modifier.height(58.dp).padding(end = 6.dp),
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colors.plannedSurface)
                .border(1.dp, colors.plannedBorder),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colors.actualSurface)
                .border(1.dp, colors.actualBorder),
        )
    }
}

@Composable
private fun PrototypeSwitcher(
    variant: TodayVariant,
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(12.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF202526)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous variant", tint = Color.White)
        }
        Column(Modifier.width(210.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${variant.key} — ${variant.label}",
                style = TimeboxTheme.type.label,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                if (selectedDate == prototypeToday) "STATE: TODAY" else "STATE: ${selectedDate.format(shortDateFormatter).uppercase(Locale.ENGLISH)}",
                style = TimeboxTheme.type.navLabel,
                color = Color(0xFFB9C5C7),
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.size(52.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next variant", tint = Color.White)
        }
    }
}
