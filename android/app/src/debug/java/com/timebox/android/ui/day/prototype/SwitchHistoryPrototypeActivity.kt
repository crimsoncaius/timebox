package com.timebox.android.ui.day.prototype

// THROWAWAY native counterpart of selected web variant A. Sample data, no repository writes.
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.undo.UndoLifecycle
import com.timebox.android.ui.undo.UndoNoticeHost
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

class SwitchHistoryPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TimeboxTheme(darkTheme = true) { SwitchStudy() } }
    }
}

private data class SampleActual(val name: String, val start: Double, val end: Double? = null, val note: String = "")
private fun fixture(overnight: Boolean) = if (overnight) listOf(
    SampleActual("Read & research", -150.0, -60.0, "Evening notes"), SampleActual("Write proposal", -30.0, note = "First draft"),
) else listOf(SampleActual("Read & research", 480.0, 600.0, "Opening section notes"),
    SampleActual("Team catch-up", 630.0, 720.0), SampleActual("Write proposal", 720.0, note = "First draft"))
private fun date(at: Double) = LocalDateTime.of(2026, 9, 25, 0, 0).plusMinutes(at.roundToLong())
private fun clock(at: Double) = date(at).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun stamp(at: Double) = date(at).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchStudy() {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var overnight by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf(fixture(false)) }
    var now by remember { mutableDoubleStateOf(840.0) }
    var selected by remember { mutableDoubleStateOf(now) }
    var name by remember { mutableStateOf("Design exploration") }
    var open by remember { mutableStateOf(true) }
    var details by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val undo = remember { UndoLifecycle(scope) }
    val notice by undo.notice.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(10_000L, false, true, true) ?: 10_000L
    DisposableEffect(lifecycle, timeout) {
        fun expose() = undo.setExposure("switch-study", lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), timeout)
        val observer = LifecycleEventObserver { _, _ -> expose() }
        lifecycle.addObserver(observer); expose()
        onDispose { lifecycle.removeObserver(observer); undo.setExposure(null, false, timeout) }
    }
    val current = records.last()
    val preview = records.filter { it.start < selected }.map { it.copy(end = min(it.end ?: selected, selected)) } + SampleActual(name, selected)
    val affected = records.filter { (it.end ?: now) > selected }
    fun reset(next: Boolean) {
        overnight = next; records = fixture(next); now = if (next) 75.0 else 840.0
        selected = now; details = false; undo.dismiss()
    }
    Surface(color = colors.bg, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("TIMEBOX", style = type.kicker)
                Text("Friday, 25 September", style = type.screenTitle)
                Text("Native prototype · A · Sample data", style = type.bodySmall, color = colors.onVariant)
                Text("● ${current.name}", style = type.sectionTitle)
                Text("Since ${stamp(current.start)} · Now ${clock(now)}", style = type.bodySmall)
                Button(onClick = { selected = now; open = true }) { Text("Switch activity") }
                Text("ACTUAL", style = type.kicker)
                records.forEach { record ->
                    Surface(color = colors.actualSurface, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(record.name, color = colors.actual, style = type.sectionTitle)
                            Text("${stamp(record.start)} → ${record.end?.let(::clock) ?: "continuing"}", style = type.bodySmall)
                        }
                    }
                }
                TextButton(onClick = { now += 1 }) { Text("Advance sample clock 1 minute") }
                TextButton(onClick = { reset(!overnight); open = true }) { Text(if (overnight) "Try daytime" else "Try across midnight") }
                Text("All changes stay in memory. Restart to reset.", style = type.bodySmall, color = colors.onVariant)
            }
            notice?.let { item -> UndoNoticeHost(item, { undo.undo(item.id) }, { undo.dismiss(item.id) }, { undo.finishExpiry(item.id) }, Modifier.align(Alignment.BottomCenter).padding(16.dp)) }
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.bg) {
        // The action row belongs to this scroll content; it never overlays the timeline.
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("ACTIVITY TRACKING", style = type.kicker, color = colors.onVariant); Text("Switch activity", style = type.screenTitle.copy(fontSize = 28.sp)) }
                TextButton(onClick = { open = false }) { Text("×", fontSize = 24.sp) }
            }
            Text("Prototype A · Sample data", style = type.bodySmall, color = colors.onVariant)
            OutlinedTextField(name, { name = it }, label = { Text("Next activity") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Starts at", style = type.bodySmall, color = colors.onVariant); Text(stamp(selected), style = type.mono) }
                TextButton(onClick = { selected = now }) { Text("Now") }
            }
            key(overnight) { StudyTimeline(records, preview, selected, now, { selected = it }) }
            Text(if (selected < current.start) "${affected.size} activities change. $name replaces time from ${stamp(selected)} through now. You can Undo."
                else "${current.name} ends at ${clock(selected)}. $name starts then. You can Undo.", style = type.bodySmall)
            if (selected < current.start) {
                TextButton(onClick = { details = !details }) { Text(if (details) "Hide changes" else "See what changes") }
                if (details) {
                    affected.forEach { record -> Text("${record.name}: ${if (record.start < selected) "ends at ${clock(selected)}" else "replaced entirely"}", style = type.bodySmall) }
                    val recorded = affected.sumOf { (it.end ?: now) - max(selected, it.start) }
                    val gap = (now - selected - recorded).roundToInt()
                    if (gap > 0) Text("$gap minutes of unrecorded time filled.", style = type.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { open = false }) { Text("Cancel") }
                Button(enabled = name.isNotBlank(), modifier = Modifier.weight(1f), onClick = {
                    val before = records
                    records = preview; open = false
                    undo.offer("switch-study", "activity switch", "Switched to $name", "From ${stamp(selected)}", undo = { records = before; Result.success(Unit) })
                }) { Text("Switch activity") }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { reset(!overnight) }) { Text(if (overnight) "Daytime sample" else "Midnight sample") }
                TextButton(onClick = { reset(overnight) }) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun StudyTimeline(records: List<SampleActual>, preview: List<SampleActual>, selected: Double, now: Double, onSelect: (Double) -> Unit) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var start by remember { mutableDoubleStateOf(now - 150) }
    var pointerY by remember { mutableStateOf<Float?>(null) }
    var handle by remember { mutableFloatStateOf(0f) }
    val select by rememberUpdatedState(onSelect)
    val currentRecords by rememberUpdatedState(records)
    val currentNow by rememberUpdatedState(now)
    val height = 280.dp
    val pixels = with(LocalDensity.current) { height.toPx() }
    val edge = with(LocalDensity.current) { 42.dp.toPx() }
    val inset = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(selected, pointerY == null) {
        if (pointerY == null && (selected < start || selected > start + 180)) start = selected - 90
    }
    LaunchedEffect(pointerY != null) {
        if (pointerY == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (pointerY != null) {
            withFrameNanos { frame ->
                val dt = ((frame - previous) / 1_000_000.0).coerceAtMost(50.0); previous = frame
                val y = pointerY?.coerceIn(0f, pixels) ?: return@withFrameNanos
                val speed = when { y < edge -> -(edge - y) / edge; y > pixels - edge -> (y - pixels + edge) / edge; else -> 0f }
                start = min(currentNow - 150, start + sign(speed) * speed * speed * dt * .135)
                val fraction = y.coerceIn(inset, pixels - inset) / pixels
                handle = min(fraction, ((currentNow - start) / 180).toFloat())
                val raw = min(currentNow, round(start + fraction * 180))
                val boundary = currentRecords.flatMap { listOfNotNull(it.start, it.end) }.minByOrNull { abs(it - raw) }?.takeIf { abs(it - raw) <= 2 }
                select(min(currentNow, boundary ?: raw))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { start -= 120 }) { Text("↑ Earlier") }
        Text(date(start).format(DateTimeFormatter.ofPattern("d MMM")), style = type.bodySmall)
        TextButton(onClick = { start = min(now - 150, start + 120) }, enabled = start < now - 150) { Text("Later ↓") }
    }
    Row(Modifier.fillMaxWidth().padding(start = 46.dp)) { Text("PLANNED", Modifier.weight(1f), style = type.kicker); Text("AFTER SWITCH", Modifier.weight(1f), style = type.kicker) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).clipToBounds()
        .pointerInput(Unit) { detectDragGestures(onDragStart = { pointerY = it.y; handle = (it.y / pixels).coerceIn(0f, 1f) }, onDragEnd = { pointerY = null }, onDragCancel = { pointerY = null }) { change, _ -> change.consume(); pointerY = change.position.y } }
        .pointerInput(Unit) { detectTapGestures { select(min(currentNow, round(start + it.y / pixels * 180))) } }) {
        val laneWidth = (maxWidth - 56.dp) / 2
        fun fraction(at: Double) = ((at - start) / 180).toFloat()
        for (index in 0..6) {
            val at = ceil(start / 30) * 30 + index * 30
            if (at <= start + 180) {
                val top = height * fraction(at)
                Text(if (at % 1440 == 0.0) date(at).format(DateTimeFormatter.ofPattern("d MMM")) else clock(at), Modifier.offset(y = top), style = type.gutter, color = colors.onVariant)
                HorizontalDivider(Modifier.offset(x = 44.dp, y = top).width(maxWidth - 44.dp), color = colors.outlineVariant)
            }
        }
        @Composable fun block(record: SampleActual, planned: Boolean = false, fresh: Boolean = false) {
            val a = max(start, record.start); val b = min(start + 180, record.end ?: now)
            if (b <= a) return
            val blockHeight = height * ((b - a) / 180).toFloat()
            Column(Modifier.offset(x = if (planned) 48.dp else 54.dp + laneWidth, y = height * fraction(a)).width(laneWidth).height(blockHeight)
                .background(if (planned) colors.plannedSurface else if (fresh) colors.actual.copy(alpha = .18f) else colors.actualSurface, RoundedCornerShape(6.dp))
                .border(1.dp, if (planned) colors.plannedBorder else if (fresh) colors.actual else colors.actualBorder, RoundedCornerShape(6.dp)).padding(6.dp)) {
                Text(record.name, style = type.blockTitle, color = if (planned) colors.planned else colors.actual, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (blockHeight > 42.dp) Text("${clock(record.start)}–${record.end?.let(::clock) ?: "now"}", style = type.monoSmall, color = colors.onVariant)
            }
        }
        block(SampleActual("Design exploration", now - 120, now), planned = true)
        preview.forEachIndexed { index, record -> block(record, fresh = index == preview.lastIndex) }
        if (now >= start && now <= start + 180) HorizontalDivider(Modifier.offset(x = 44.dp, y = height * fraction(now)).width(maxWidth - 44.dp), color = colors.now)
        val line = if (pointerY != null) handle else fraction(selected)
        if (pointerY != null || line in 0f..1f) {
            HorizontalDivider(Modifier.offset(x = 44.dp, y = height * line).width(maxWidth - 44.dp), thickness = 2.dp, color = colors.on)
            Surface(Modifier.align(Alignment.TopEnd).offset(y = height * line - 14.dp), color = colors.on, contentColor = colors.bg, shape = RoundedCornerShape(6.dp)) {
                Text("↕ ${clock(selected)}", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = type.monoSmall)
            }
        }
    }
    Text("Drag the white line. Hold near an edge to keep scrolling.", style = type.bodySmall, color = colors.onVariant)
}
