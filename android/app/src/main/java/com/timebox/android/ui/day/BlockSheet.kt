package com.timebox.android.ui.day

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timebox.android.data.ActualBlock
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.components.ContextualHelp
import com.timebox.android.ui.components.CardLink
import com.timebox.android.ui.components.LinkedTaskRow
import com.timebox.android.ui.components.RelatedCard
import com.timebox.android.ui.components.SheetDeleteRow
import com.timebox.android.ui.components.SheetField
import com.timebox.android.ui.components.SheetHelp
import com.timebox.android.ui.components.SheetOutlinedButton
import com.timebox.android.ui.components.SheetPrimary
import com.timebox.android.ui.components.SheetTextField
import com.timebox.android.ui.components.SheetValueField
import com.timebox.android.ui.components.TimeboxDialog
import com.timebox.android.ui.components.TimeboxSheet
import com.timebox.android.ui.components.TransientFeedback
import com.timebox.android.ui.elapsedDuration
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Planned Block sheet, for a saved block or a new one. Every edit stays in the sheet until
 * Save (or Create block); dismissing discards it. Actual blocks open [ActivityActualEditor].
 *
 * @param onChangeTimes records the time fields' values; dragging on the timeline saves on its own.
 */
@Composable
fun BlockSheet(
    state: DayUiState,
    onDismiss: () -> Unit,
    onChooseType: (TaskType) -> Unit,
    onTypeQueryChange: (String) -> Unit,
    onCreateType: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCreateDraft: () -> Unit,
    onDelete: () -> Unit,
    onConfirmTaskCompletion: () -> Unit,
    onReopenTask: () -> Unit,
    onOpenLinkedTask: (Int) -> Unit,
    allowComplete: Boolean = true,
    onChangeTimes: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onRecordPlanned: () -> Unit = {},
    onOpenActual: (Int) -> Unit = {},
    onUndoRecording: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    if (state.sheetLane == Lane.Actual) {
        ActivityActualEditor(state, onDismiss, onOpenLinkedTask = onOpenLinkedTask, onOpenPlanned = onOpenActual)
        return
    }
    val colors = TimeboxTheme.colors
    val isDraft = state.draft != null
    val block = state.selectedBlock
    val range = "${hhmm(state.sheetStart)} – ${hhmm(state.sheetEnd)}"
    val timesError = block?.let { plannedTimesError(it, state.day, state.sheetStart, state.sheetEnd) }
    var deleting by remember(state.selectedBlockId) { mutableStateOf(false) }

    TimeboxSheet(
        lane = Lane.Planned,
        kicker = if (isDraft) "Planned block · New" else "Planned block",
        title = range,
        meta = elapsedDuration((state.sheetEnd - state.sheetStart).toLong().coerceAtLeast(0)),
        onDismiss = onDismiss,
        busy = state.saving && isDraft,
        primary = when {
            isDraft -> if (state.saving) SheetPrimary.Disabled("Creating…") else SheetPrimary.Action("Create block", onCreateDraft)
            state.saving -> SheetPrimary.Disabled("Saving…")
            timesError != null -> SheetPrimary.Disabled("Fix the times to save")
            !state.plannedSheetChanged -> SheetPrimary.Disabled("No changes")
            else -> SheetPrimary.Action("Save changes", onSave)
        },
    ) {
        if (block != null) {
            PlannedTimeFields(state.sheetStart, state.sheetEnd, enabled = !state.saving && block.googleCalendar == null) { start, end -> onChangeTimes(block.id, start, end) }
            timesError?.let { SheetHelp(it, color = colors.error) }
            if (block.googleCalendar == null) ContextualHelp("Adjust block time", "Drag the block on the timeline to move it, or pull its grooves to resize.")
            else {
                SheetHelp("Name and time follow Google Calendar. Local classification and notes save here.")
                val context = LocalContext.current
                block.googleCalendar.url?.let { url -> androidx.compose.material3.TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }) { Text("Open source event") } }
            }
            val now = produceState(Instant.now()) {
                while (true) { kotlinx.coroutines.delay(1000); value = Instant.now() }
            }.value
            PlannedRecordingCards(
                block = block,
                day = state.day,
                now = now,
                saving = state.saving,
                notice = state.recordingNotice?.takeIf { it.first == state.selectedBlockId }?.second,
                error = state.recordingError,
                onRecord = onRecordPlanned,
                onOpenActual = onOpenActual,
            )
            block.task?.let { task ->
                SheetField("Battle Plan task") {
                    LinkedTaskRow(task.title, "Linked to this block", onOpen = { onOpenLinkedTask(task.id) })
                }
            }
        }
        if (block?.googleCalendar != null) Text(block.name ?: "Busy", style = TimeboxTheme.type.sectionTitle)
        else SheetTextField(
            state.nameInput, onNameChange, "Name", optional = true, placeholder = "Add a name",
            fieldModifier = Modifier.testTag("block-name-input"),
        )
        SheetField("Task type") {
            TaskTypePicker(
                recommendationName = state.nameInput, recommendationLinkedTaskName = block?.task?.title ?: state.readyTasks.find { it.id == state.draft?.taskId }?.title, recommendationEnabled = !state.saving,
                taskTypes = state.taskTypes,
                query = state.typeQuery,
                onQueryChange = onTypeQueryChange,
                selectedTypeId = state.typeInput ?: block?.taskTypeId ?: state.draft?.taskTypeId,
                onChoose = onChooseType,
                onCreate = onCreateType,
            )
        }
        SheetTextField(state.noteInput, onNoteChange, "Note", optional = true, placeholder = "Add a note", singleLine = false, minLines = 3, maxLines = 4)
        block?.task?.let { task ->
            Row(
                Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Task completed", style = TimeboxTheme.type.label)
                    Text(
                        if (task.isReadOnly) "Inactive Battle Plan tasks are read-only."
                        else "No further work remains for this task.",
                        style = TimeboxTheme.type.bodySmall,
                        color = colors.onVariant,
                    )
                }
                Checkbox(
                    checked = task.status == com.timebox.android.data.TaskStatus.Completed,
                    enabled = !task.isReadOnly && !state.saving,
                    onCheckedChange = { completed ->
                        if (completed) onConfirmTaskCompletion() else onReopenTask()
                    },
                )
            }
        }
        if (block?.googleCalendar != null && !block.googleCalendar.frozen) androidx.compose.material3.TextButton(onClick = { deleting = true }, enabled = !state.saving) { Text("Leave this meeting out") }
        else if (block != null) SheetDeleteRow(onClick = { deleting = true }, enabled = !state.saving)
    }

    if (deleting && block != null) TimeboxDialog(
        lane = Lane.Planned,
        kicker = "Planned block",
        title = if (block.googleCalendar != null && !block.googleCalendar.frozen) "Leave this meeting out?" else "Delete planned block ${hhmm(block.startMinute)} – ${hhmm(block.endMinute)}?",
        body = if (block.googleCalendar != null) "The Timebox plan is removed. The Google event and recorded activity stay unchanged." else "The plan is removed. Recorded time is unchanged.",
        onDismiss = { deleting = false },
        destructive = true,
        primary = SheetPrimary.Action(if (block.googleCalendar != null && !block.googleCalendar.frozen) "Leave out" else "Delete block") { deleting = false; onDelete() },
    )
}

/** Why the Planned sheet's typed times cannot be saved, or null when they can. */
internal fun plannedTimesError(block: TimeBlock, day: Day?, start: Int, end: Int): String? = when {
    end <= start -> "End must be after start."
    day != null && (start < day.visibleStart || end > day.visibleEnd) -> "Choose times within this day."
    day?.lane(block.lane)?.any { it.id != block.id && start < it.endMinute && end > it.startMinute } == true ->
        "This time overlaps another block."
    else -> null
}

@Composable
private fun PlannedTimeFields(start: Int, end: Int, enabled: Boolean, onChange: (Int, Int) -> Unit) {
    val context = LocalContext.current
    fun pick(minute: Int, onPicked: (Int) -> Unit) {
        val at = minute.coerceIn(0, 1439)
        TimePickerDialog(context, { _, hour, next -> onPicked(hour * 60 + next) }, at / 60, at % 60, true).show()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetValueField("Start", hhmm(start), { pick(start) { onChange(it, end) } }, Modifier.weight(1f), enabled, contentDescription = "Start time")
        SheetValueField("End", hhmm(end), { pick(end) { onChange(start, if (it == 0) 1440 else it) } }, Modifier.weight(1f), enabled, contentDescription = "End time")
    }
}

/** Recorded time that belongs to this plan, as related cards, and the action that records it. */
@Composable
private fun PlannedRecordingCards(
    block: TimeBlock,
    day: Day?,
    now: Instant,
    saving: Boolean,
    notice: String?,
    error: String?,
    onRecord: () -> Unit,
    onOpenActual: (Int) -> Unit,
) {
    val zone = ZoneId.of(day?.timezone ?: "UTC")
    val recording = plannedRecordingState(block, day, now)
    val requestedRange = "${hhmm(recording.requestedStartMinute)} – ${hhmm(recording.requestedEndMinute)}"
    val requestedDuration = elapsedDuration(recording.requestedDurationMinutes ?: (recording.requestedEndMinute - recording.requestedStartMinute).toLong().coerceAtLeast(0))
    when (recording.kind) {
        PlannedRecordingKind.AlreadyRecorded -> {
            val linked = recording.matching!!
            RelatedCard(
                lane = Lane.Actual,
                caption = "Already recorded · no update needed",
                trailing = elapsedDuration((linked.endMinute - linked.startMinute).toLong()),
                title = "${hhmm(linked.startMinute)} – ${hhmm(linked.endMinute)}",
                text = "Plan notes stay on the plan.",
            ) { CardLink("Open actual", Lane.Actual, { onOpenActual(linked.id) }) }
        }
        PlannedRecordingKind.Override -> {
            val linked = recording.override!!
            RelatedCard(
                lane = Lane.Actual,
                caption = "Recorded during this plan · times differ",
                trailing = elapsedDuration((linked.endMinute - linked.startMinute).toLong()),
                title = "${hhmm(linked.startMinute)} – ${hhmm(linked.endMinute)}",
                text = if (recording.available) "Plan times: $requestedRange ($requestedDuration)." else "Available after this block starts.",
            ) {
                CardLink("Open actual", Lane.Actual, { onOpenActual(linked.id) })
                SheetOutlinedButton("Replace with plan times…", onRecord, enabled = !saving && recording.available, compact = true)
            }
        }
        PlannedRecordingKind.Record -> {
            block.actualBlockIds.forEach { id ->
                val actual = day?.actualBlocks?.find { it.actualBlock.id == id }?.actualBlock
                RelatedCard(
                    lane = Lane.Actual,
                    caption = "Linked Actual · times may differ from this plan",
                    trailing = actual?.let { elapsedDuration(Duration.between(it.startAt, it.endAt ?: now).toMinutes().coerceAtLeast(0)) },
                    title = actual?.let { formatActualRange(it, zone) } ?: "Linked Actual",
                ) { CardLink("Open actual", Lane.Actual, { onOpenActual(id) }) }
            }
            RelatedCard(
                lane = Lane.Actual,
                caption = if (!recording.available) "Available after this block starts."
                    else if (block.actualBlockIds.isNotEmpty()) "Record again from this plan" else "Record from this plan",
                trailing = requestedDuration,
                title = requestedRange,
            ) {
                SheetOutlinedButton(
                    if (recording.underway) "Record Actual until now" else "Record Actual as planned",
                    onRecord, enabled = !saving && recording.available, compact = true,
                )
            }
        }
    }
    notice?.let { TransientFeedback(it) }
    error?.let { Text(it, color = TimeboxTheme.colors.error, style = TimeboxTheme.type.bodySmall) }
}

private fun formatActualRange(actual: ActualBlock, zone: ZoneId): String {
    val start = actual.startAt.atZone(zone)
    val end = actual.endAt?.atZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val dateTime = DateTimeFormatter.ofPattern("d MMM HH:mm")
    val format = if (end != null && start.toLocalDate() == end.toLocalDate()) clock else dateTime
    return "${format.format(start)} – ${end?.let(format::format) ?: "Now"}"
}
