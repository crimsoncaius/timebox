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
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PROTOTYPE — throwaway code.
 *
 * Three one-line Day title strategies, switchable via
 * timebox://prototype/one-line-day-title?variant=A in a debug build.
 */
class OneLineDayTitlePrototypeActivity : ComponentActivity() {
    private var activeVariant by mutableStateOf(OneLineTitleVariant.A)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        activeVariant = OneLineTitleVariant.from(intent.data)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            TimeboxTheme(darkTheme = true) {
                OneLineDayTitlePrototype(
                    variant = activeVariant,
                    onVariantChange = ::showVariant,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeVariant = OneLineTitleVariant.from(intent.data)
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

    private fun showVariant(variant: OneLineTitleVariant) {
        activeVariant = variant
        setIntent(
            Intent(intent).setData(
                Uri.parse("timebox://prototype/one-line-day-title?variant=${variant.key}"),
            ),
        )
    }
}

private enum class OneLineTitleVariant(val key: String, val label: String) {
    A("A", "Full date + compact actions"),
    B("B", "Short weekday + current actions"),
    C("C", "Weekday badge + month/day");

    fun previous(): OneLineTitleVariant = entries[(ordinal - 1 + entries.size) % entries.size]
    fun next(): OneLineTitleVariant = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(uri: Uri?): OneLineTitleVariant {
            val requested = uri?.getQueryParameter("variant")?.uppercase(Locale.ENGLISH)
            return entries.firstOrNull { it.key == requested } ?: A
        }
    }
}

private val prototypeToday = LocalDate.of(2026, 9, 2)
private val fullTitleFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
private val shortWeekdayFormatter = DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.ENGLISH)
private val monthDayFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)
private val accessibilityDateFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH)

@Composable
private fun OneLineDayTitlePrototype(
    variant: OneLineTitleVariant,
    onVariantChange: (OneLineTitleVariant) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var selectedDate by remember { mutableStateOf(prototypeToday) }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 92.dp),
        ) {
            PrototypeHeader(
                variant = variant,
                selectedDate = selectedDate,
                onNavigateToday = { selectedDate = prototypeToday },
            )
            WeekStrip(selectedDate = selectedDate, onSelectDate = { selectedDate = it })
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(12.dp))
            TimelinePreview()
        }

        PrototypeVariantSwitcher(
            variant = variant,
            onPrevious = { onVariantChange(variant.previous()) },
            onNext = { onVariantChange(variant.next()) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PrototypeHeader(
    variant: OneLineTitleVariant,
    selectedDate: LocalDate,
    onNavigateToday: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("DAY", style = TimeboxTheme.type.navLabel, color = colors.onVariant)
                when (variant) {
                    OneLineTitleVariant.A -> OneLineTitle(
                        text = selectedDate.format(fullTitleFormatter),
                        fontSize = 20,
                        selectedDate = selectedDate,
                    )
                    OneLineTitleVariant.B -> OneLineTitle(
                        text = selectedDate.format(shortWeekdayFormatter),
                        fontSize = 23,
                        selectedDate = selectedDate,
                    )
                    OneLineTitleVariant.C -> BadgeTitle(selectedDate)
                }
            }
            when (variant) {
                OneLineTitleVariant.A -> CompactActions()
                OneLineTitleVariant.B,
                OneLineTitleVariant.C,
                -> CurrentActions()
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TodayAction(enabled = selectedDate != prototypeToday, onClick = onNavigateToday)
            Spacer(Modifier.weight(1f))
            ModeControl()
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun OneLineTitle(text: String, fontSize: Int, selectedDate: LocalDate) {
    Text(
        text = text,
        style = TimeboxTheme.type.screenTitle.copy(fontSize = fontSize.sp, lineHeight = 27.sp),
        color = TimeboxTheme.colors.on,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics {
            contentDescription = selectedDate.format(accessibilityDateFormatter)
        },
    )
}

@Composable
private fun BadgeTitle(selectedDate: LocalDate) {
    val colors = TimeboxTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            contentDescription = selectedDate.format(accessibilityDateFormatter)
        },
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(colors.low)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                selectedDate.dayOfWeek.name.take(3),
                style = TimeboxTheme.type.navLabel.copy(fontWeight = FontWeight.SemiBold),
                color = colors.planned,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            selectedDate.format(monthDayFormatter),
            style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, lineHeight = 27.sp),
            color = colors.on,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactActions() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactIconAction(Icons.Outlined.PlayArrow, "Work Mode")
        CompactIconAction(Icons.Outlined.Checklist, "Plan")
    }
}

@Composable
private fun CompactIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val colors = TimeboxTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable {}
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CurrentActions() {
    val colors = TimeboxTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactIconAction(Icons.Outlined.PlayArrow, "Work Mode")
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(percent = 50))
                .clickable {},
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .border(1.dp, colors.plannedBorder, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    Icons.Outlined.Checklist,
                    contentDescription = null,
                    tint = colors.planned,
                    modifier = Modifier.size(17.dp),
                )
                Text("Plan", style = TimeboxTheme.type.label, color = colors.planned)
            }
        }
    }
}

@Composable
private fun TodayAction(enabled: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val contentColor = if (enabled) colors.onVariant else colors.disabledContent
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) colors.low else colors.disabledContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(15.dp),
        )
        Text("Today", style = TimeboxTheme.type.bodySmall, color = contentColor)
    }
}

@Composable
private fun ModeControl() {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .width(116.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.low)
            .padding(2.dp),
    ) {
        listOf("Week" to true, "Month" to false).forEach { (label, selected) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) colors.lowest else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = TimeboxTheme.type.navLabel,
                    color = if (selected) colors.on else colors.onVariant,
                )
            }
        }
    }
}

@Composable
private fun WeekStrip(selectedDate: LocalDate, onSelectDate: (LocalDate) -> Unit) {
    val monday = selectedDate.with(DayOfWeek.MONDAY)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(7) { index ->
            val date = monday.plusDays(index.toLong())
            WeekDate(date, selectedDate, onSelectDate, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekDate(
    date: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val colors = TimeboxTheme.colors
    val selected = date == selectedDate
    val isToday = date == prototypeToday
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .height(54.dp)
            .then(if (isToday && !selected) Modifier.border(1.dp, colors.planned, shape) else Modifier)
            .clip(shape)
            .background(if (selected) colors.selected else if (isToday) colors.plannedSurface else Color.Transparent)
            .semantics { contentDescription = date.format(accessibilityDateFormatter) }
            .clickable { onSelectDate(date) }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfWeek.name.take(3),
            style = TimeboxTheme.type.navLabel,
            color = if (selected) colors.onSelected else colors.onVariant,
        )
        Text(
            date.dayOfMonth.toString(),
            style = TimeboxTheme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.onSelected else if (isToday) colors.planned else colors.onVariant,
        )
    }
}

@Composable
private fun TimelinePreview() {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
    ) {
        Spacer(Modifier.width(TimeboxDimens.gutterWidth))
        Text("PLANNED", style = TimeboxTheme.type.laneLabel, color = colors.planned, modifier = Modifier.weight(1f))
        Text("ACTUAL", style = TimeboxTheme.type.laneLabel, color = colors.actual, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = TimeboxDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
    ) {
        Column(Modifier.width(TimeboxDimens.gutterWidth), horizontalAlignment = Alignment.End) {
            listOf("8 AM", "9 AM", "10 AM", "11 AM").forEach { label ->
                Text(
                    label,
                    style = TimeboxTheme.type.gutter,
                    color = colors.timelineLabel,
                    modifier = Modifier.height(52.dp).padding(end = 6.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.plannedSurface).border(1.dp, colors.plannedBorder))
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.actualSurface).border(1.dp, colors.actualBorder))
    }
}

@Composable
private fun PrototypeVariantSwitcher(
    variant: OneLineTitleVariant,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF202526)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous variant", tint = Color.White)
        }
        Text(
            "${variant.key} — ${variant.label}",
            style = TimeboxTheme.type.label,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(228.dp),
        )
        Box(Modifier.size(48.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next variant", tint = Color.White)
        }
    }
}
