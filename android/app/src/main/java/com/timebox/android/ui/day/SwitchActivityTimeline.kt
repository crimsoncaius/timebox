package com.timebox.android.ui.day

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

@Composable
internal fun SwitchActivityTimeline(
    currentId: Int, start: Instant, records: List<ActualBlockDto>, plans: List<ActivityPlanDto>,
    taskTypes: List<TaskType>, nextActivity: String?, selected: Instant, now: Instant, zone: ZoneId,
    enabled: Boolean, onSelect: (Instant?) -> Unit,
    loadPlanTitles: suspend (java.time.LocalDate) -> Map<Int, String> = { emptyMap() },
) {
    val stopping = nextActivity == null
    val tag = if (stopping) "stop" else "switch"
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var window by remember(currentId) { mutableStateOf(SwitchTimelineWindow.around(selected)) }
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
    fun planTitle(plan: ActivityPlanDto): String = plan.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: planTitles[plan.id]
        ?: taskTypes.find { it.id == plan.taskTypeId }?.name?.takeUnless { it == "unspecified" }
        ?: if (plan.taskId != null) "Linked task" else "Untitled"
    val boundaries = remember(plans, records, start) {
        (plans.flatMap { listOf(parseActivityInstant(it.startAt), parseActivityInstant(it.endAt)) } +
            records.flatMap { listOfNotNull(parseActivityInstant(it.startAt), it.endAt?.let(::parseActivityInstant)) } + start).distinct()
    }
    val selectFraction by rememberUpdatedState<(Float) -> Unit> { fraction ->
        val at = window.select(fraction, start, now, boundaries)
        onSelect(if (at >= now) null else at)
    }
    val label = switchTimeLabel(selected, zone)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { window = window.shift(-2) }, enabled = enabled && window.start > start.minusSeconds(3600),
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
            Text("RECORDED", Modifier.weight(1f).testTag("$tag-recorded-header"), style = type.laneLabel, color = colors.actual)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(264.dp)) {
            val width = maxWidth
            val height = 240.dp
            val lane = (width - 58.dp) / 2
            val gesture = if (enabled) Modifier.pointerInput(window) {
                detectTapGestures { selectFraction(it.y / size.height) }
            }.pointerInput(window) {
                detectDragGestures { change, _ -> change.consume(); selectFraction(change.position.y / size.height) }
            } else Modifier
            Box(Modifier.fillMaxWidth().height(height).testTag("$tag-timeline").then(gesture).semantics {
                contentDescription = if (stopping) "Stop time timeline" else "Switch time timeline"
                stateDescription = label
                if (enabled) {
                    progressBarRangeInfo = ProgressBarRangeInfo(window.fraction(selected).coerceIn(0f, 1f), 0f..1f)
                    setProgress { selectFraction(it); true }
                    customActions = listOf(
                        CustomAccessibilityAction("One minute earlier") { onSelect(selected.minusSeconds(60).coerceAtLeast(start)); true },
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
                    if (record.id == currentId) selected else record.endAt?.let(::parseActivityInstant) ?: now,
                    record.name?.takeIf { it.isNotBlank() } ?: record.taskType.name, false) }
                // Stopping leaves the recorded lane empty from the selected end onward.
                // Extend through the visible future so "Now" still previews the unrecorded state.
                block(selected, if (stopping) maxOf(now, window.end) else now,
                    nextActivity ?: "Unrecorded", false, preview = true, unrecorded = stopping)
                val nowFraction = window.fraction(now)
                if (nowFraction in 0f..1f) HorizontalDivider(Modifier.offset(x = 50.dp, y = height * nowFraction).width(width - 50.dp), color = colors.now)
                val fraction = window.fraction(selected)
                if (fraction in 0f..1f) {
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
        }.filter { (at, _) -> at >= start && at <= now && at >= window.start && at <= window.end }
            .sortedBy { java.time.Duration.between(selected, it.first).abs() }.distinctBy { it.first }.take(2)
        shortcuts.forEach { (at, title) ->
            SuggestionChip(onClick = { onSelect(at) }, enabled = enabled, label = {
                Text("$title · ${switchTimeLabel(at, zone)}", style = type.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }, modifier = Modifier.heightIn(min = 40.dp))
        }
    }
}
