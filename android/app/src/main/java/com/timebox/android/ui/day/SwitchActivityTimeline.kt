package com.timebox.android.ui.day

import com.timebox.android.data.activityIdentityText
import com.timebox.android.data.identityText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timebox.android.data.TaskType
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sign

@Composable
internal fun SwitchActivityTimeline(
    currentId: Int, start: Instant, records: List<ActualBlockDto>, plans: List<ActivityPlanDto>,
    taskTypes: List<TaskType>, nextActivity: String?, selected: Instant, now: Instant, zone: ZoneId,
    enabled: Boolean, onSelect: (Instant?) -> Unit,
    loadPlanTitles: suspend (java.time.LocalDate) -> Map<Int, String> = { emptyMap() },
    allowHistory: Boolean = false,
) {
    val stopping = nextActivity == null
    val tag = if (stopping) "stop" else "switch"
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var window by remember(currentId) { mutableStateOf(SwitchTimelineWindow.around(selected)) }
    val earliest = if (allowHistory && !stopping) Instant.MIN else start
    var dragY by remember(currentId) { mutableStateOf<Float?>(null) }
    var handle by remember(currentId) { mutableFloatStateOf(0f) }
    val pixels = with(LocalDensity.current) { 240.dp.toPx() }
    val edge = with(LocalDensity.current) { 42.dp.toPx() }
    val inset = with(LocalDensity.current) { 14.dp.toPx() }
    val visiblePlans = remember(plans, window) {
        plans.filter { parseActivityInstant(it.startAt) < window.end && parseActivityInstant(it.endAt) > window.start }
    }
    var planTitles by remember { mutableStateOf(emptyMap<Int, String>()) }
    val titleLoader by rememberUpdatedState(loadPlanTitles)
    val titleDates = visiblePlans.filter { it.taskId != null && it.name.isNullOrBlank() }
        .map { parseActivityInstant(it.startAt).atZone(zone).toLocalDate() }.distinct()
    LaunchedEffect(titleDates, plans) {
        for (date in titleDates) planTitles = planTitles + titleLoader(date)
    }
    fun planTitle(plan: ActivityPlanDto): String = activityIdentityText(plan.name, plan.taskTitle ?: planTitles[plan.id], taskTypes.find { it.id == plan.taskTypeId }?.name)
    val boundaries = remember(plans, records, start) {
        (plans.flatMap { listOf(parseActivityInstant(it.startAt), parseActivityInstant(it.endAt)) } +
            records.flatMap { listOfNotNull(parseActivityInstant(it.startAt), it.endAt?.let(::parseActivityInstant)) } + start).distinct()
    }
    val selectFraction by rememberUpdatedState<(Float) -> Unit> { fraction ->
        val at = window.select(fraction, earliest, now, boundaries)
        onSelect(if (at >= now) null else at)
    }
    val label = switchTimeLabel(selected, zone)
    val latestNow by rememberUpdatedState(now)
    LaunchedEffect(dragY != null, enabled) {
        if (!enabled || dragY == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (dragY != null) withFrameNanos { frame ->
            val dt = ((frame - previous) / 1_000_000.0).coerceAtMost(50.0); previous = frame
            val y = dragY?.coerceIn(0f, pixels) ?: return@withFrameNanos
            val speed = when { y < edge -> -(edge - y) / edge; y > pixels - edge -> (y - pixels + edge) / edge; else -> 0f }
            val lower = if (allowHistory && !stopping) Instant.MIN else start.minusSeconds(3600)
            window = SwitchTimelineWindow(window.start.plusMillis((sign(speed) * speed * speed * dt * 8100).toLong())
                .coerceIn(lower, maxOf(lower, SwitchTimelineWindow.around(latestNow).start)))
            val minimum = if (earliest == Instant.MIN) 0f else window.fraction(earliest).coerceIn(0f, 1f)
            handle = (y.coerceIn(inset, pixels - inset) / pixels).coerceIn(minimum, maxOf(minimum, window.fraction(latestNow).coerceIn(0f, 1f)))
            selectFraction(handle)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { window = window.shift(-2) }, enabled = enabled && (allowHistory && !stopping || window.start > start.minusSeconds(3600)),
                modifier = Modifier.semantics { contentDescription = "Earlier context" }, contentPadding = PaddingValues(4.dp)) { Text("‹", style = type.sectionTitle) }
            Column(Modifier.weight(1f)) {
                val dates = if (window.start.atZone(zone).toLocalDate() == window.end.atZone(zone).toLocalDate())
                    window.start.atZone(zone).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
                else "${window.start.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM"))} – ${window.end.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM"))}"
                Text(dates, style = type.bodySmall)
                Text("${switchTimeLabel(window.start, zone)}–${switchTimeLabel(window.end, zone)} · ${zone.id}", style = type.monoSmall, color = colors.onVariant)
            }
            TextButton(onClick = { window = window.shift(2) }, enabled = enabled && window.end < now,
                modifier = Modifier.semantics { contentDescription = "Later context" }, contentPadding = PaddingValues(4.dp)) { Text("›", style = type.sectionTitle) }
            TextButton(onClick = { onSelect(null); window = SwitchTimelineWindow.around(now) }, enabled = enabled) { Text("Now", style = type.bodySmall) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 52.dp)) {
            Text("PLANNED", Modifier.weight(1f).testTag("$tag-planned-header"), style = type.laneLabel, color = colors.planned)
            Text(if (stopping) "RECORDED" else "AFTER SWITCH", Modifier.weight(1f).testTag("$tag-recorded-header"), style = type.laneLabel, color = colors.actual)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(264.dp)) {
            val width = maxWidth
            val height = 240.dp
            val lane = (width - 58.dp) / 2
            val gesture = if (enabled) Modifier.pointerInput(currentId) {
                detectTapGestures { selectFraction(it.y / size.height) }
            }.pointerInput(currentId) {
                detectDragGestures(onDragStart = { dragY = it.y; handle = (it.y / size.height).coerceIn(0f, 1f) }, onDragEnd = { dragY = null }, onDragCancel = { dragY = null }) { change, _ -> change.consume(); dragY = change.position.y }
            } else Modifier
            Box(Modifier.fillMaxWidth().height(height).clipToBounds().testTag("$tag-timeline").then(gesture).semantics {
                contentDescription = if (stopping) "Stop time timeline" else "Switch time timeline"
                stateDescription = label
                if (enabled) {
                    progressBarRangeInfo = ProgressBarRangeInfo(window.fraction(selected).coerceIn(0f, 1f), 0f..1f)
                    setProgress { selectFraction(it); true }
                    customActions = listOf(
                        CustomAccessibilityAction("One minute earlier") { onSelect(selected.minusSeconds(60).coerceAtLeast(earliest)); true },
                        CustomAccessibilityAction("One minute later") { onSelect(selected.plusSeconds(60).coerceAtMost(now)); true },
                    )
                } else disabled()
            }) {
                (0..6).forEach { index ->
                    val at = window.start.plusSeconds(index * 1800L)
                    Text(switchTimeLabel(at, zone).replace(" ", "\n"), Modifier.offset(y = height * (index / 6f)), style = type.gutter, color = colors.onVariant)
                    HorizontalDivider(Modifier.offset(x = 50.dp, y = height * (index / 6f)).width(width - 50.dp), color = colors.hairline)
                }
                @Composable fun block(a: Instant, b: Instant, title: String, planned: Boolean, preview: Boolean = false, unrecorded: Boolean = false) {
                    val top = window.fraction(a).coerceIn(0f, 1f)
                    val bottom = window.fraction(b).coerceIn(0f, 1f)
                    if (bottom <= top) return
                    val accent = if (unrecorded) colors.onVariant else if (planned) colors.planned else colors.actual
                    val blockHeight = height * (bottom - top)
                    Column(Modifier.offset(x = if (planned) 52.dp else 58.dp + lane, y = height * top)
                        .width(lane).height(blockHeight).clip(TimeboxShapes.block)
                        .background(accent.copy(alpha = if (preview) .23f else .13f).compositeOver(colors.bg))
                        .border(1.dp, if (unrecorded) colors.outlineVariant else if (planned) colors.plannedBorder else colors.actualBorder, TimeboxShapes.block)
                        .padding(6.dp)) {
                        Text(title, style = type.blockTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (blockHeight >= 40.dp) Text("${switchTimeLabel(a, zone)}–${switchTimeLabel(b, zone)}", style = type.monoSmall,
                            color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                visiblePlans.forEach { plan -> block(parseActivityInstant(plan.startAt), parseActivityInstant(plan.endAt),
                    planTitle(plan), true) }
                records.forEach { record -> block(parseActivityInstant(record.startAt),
                    if (!stopping && allowHistory) minOf(selected, record.endAt?.let(::parseActivityInstant) ?: now) else if (record.id == currentId) selected else record.endAt?.let(::parseActivityInstant) ?: now,
                    record.identityText(), false) }
                // Stopping leaves the recorded lane empty from the selected end onward.
                // Extend through the visible future so "Now" still previews the unrecorded state.
                block(selected, if (stopping) maxOf(now, window.end) else now,
                    nextActivity ?: "Unrecorded", false, preview = true, unrecorded = stopping)
                val nowFraction = window.fraction(now)
                if (nowFraction in 0f..1f) HorizontalDivider(Modifier.offset(x = 50.dp, y = height * nowFraction).width(width - 50.dp), color = colors.now)
                val fraction = if (dragY != null) handle else window.fraction(selected)
                if (dragY != null || fraction in 0f..1f) {
                    HorizontalDivider(Modifier.offset(x = 50.dp, y = height * fraction).width(width - 50.dp), thickness = 2.dp, color = colors.primary)
                    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = TimeboxShapes.chip,
                        modifier = Modifier.align(Alignment.TopEnd).offset(y = height * fraction - 12.dp)) {
                        Text("↕ $label", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = type.monoSmall)
                    }
                }
            }
            Text("Now · ${switchTimeLabel(now, zone)}", Modifier.align(Alignment.BottomEnd), style = type.monoSmall, color = colors.onVariant)
        }
        if (visiblePlans.isEmpty()) Text("No planned blocks in this window.", style = type.bodySmall, color = colors.onVariant)
        val shortcuts = visiblePlans.flatMap { plan ->
            val title = planTitle(plan)
            listOf(parseActivityInstant(plan.startAt) to "$title starts", parseActivityInstant(plan.endAt) to "$title ends")
        }.filter { (at, _) -> at >= earliest && at <= now && at >= window.start && at <= window.end }
            .sortedBy { java.time.Duration.between(selected, it.first).abs() }.distinctBy { it.first }.take(2)
        shortcuts.forEach { (at, title) ->
            SuggestionChip(onClick = { onSelect(at) }, enabled = enabled, label = {
                Text("$title · ${switchTimeLabel(at, zone)}", style = type.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }, modifier = Modifier.heightIn(min = 40.dp))
        }
    }
}
