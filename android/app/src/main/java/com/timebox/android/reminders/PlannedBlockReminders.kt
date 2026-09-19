package com.timebox.android.reminders

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Device-local Planned Block Reminder preference: one global lead time for every Planned Block. */
data class PlannedBlockReminderSettings(val enabled: Boolean = false, val leadMinutes: Int = DEFAULT_LEAD_MINUTES) {
    companion object {
        const val DEFAULT_LEAD_MINUTES = 5
        val LeadOptions = listOf(0, 1, 5, 10, 15, 30)
    }
}

fun plannedBlockLeadLabel(minutes: Int): String = if (minutes == 0) "At start" else "$minutes min before"

/** The facts a reminder needs about one Planned Block. */
data class ReminderBlock(val id: Int, val start: Instant, val end: Instant, val title: String)

/** A block is reminded at most once per start time, so moving a block re-arms its reminder. */
fun plannedBlockReminderKey(blockId: Int, start: Instant): String = "$blockId@$start"
val ReminderBlock.reminderKey get() = plannedBlockReminderKey(id, start)
fun plannedBlockIdOf(key: String): Int? = key.substringBefore('@').toIntOrNull()
fun plannedBlockStartOf(key: String): Instant? = runCatching { Instant.parse(key.substringAfter('@')) }.getOrNull()

data class PlannedBlockAlarm(val key: String, val blockId: Int, val fireAt: Instant)

data class PlannedBlockReminderPlan(
    val alarms: List<PlannedBlockAlarm>,
    /** Blocks learned of only after they started. They are handled without a notification. */
    val skipped: Set<String>,
)

/**
 * Pure scheduling decision. A block first learned of after its reminder time fires immediately
 * if it has not started, and is skipped if it has. A block that already had a pending reminder
 * keeps it even when delivery runs late (inexact alarms).
 */
fun planPlannedBlockReminders(
    blocks: List<ReminderBlock>,
    settings: PlannedBlockReminderSettings,
    handled: Set<String>,
    scheduled: Set<String>,
    now: Instant,
): PlannedBlockReminderPlan {
    if (!settings.enabled) return PlannedBlockReminderPlan(emptyList(), emptySet())
    val alarms = mutableListOf<PlannedBlockAlarm>()
    val skipped = mutableSetOf<String>()
    blocks.sortedBy(ReminderBlock::start).distinctBy(ReminderBlock::id).forEach { block ->
        val key = block.reminderKey
        if (key in handled) return@forEach
        if (block.start < now && key !in scheduled) {
            skipped += key
            return@forEach
        }
        val ideal = block.start.minus(Duration.ofMinutes(settings.leadMinutes.toLong()))
        alarms += PlannedBlockAlarm(key, block.id, if (ideal < now) now else ideal)
    }
    return PlannedBlockReminderPlan(alarms, skipped)
}

sealed interface PlannedBlockReminderDecision {
    data object Deliver : PlannedBlockReminderDecision
    /** The block moved or disappeared; the next reconcile schedules its new start instead. */
    data object Stale : PlannedBlockReminderDecision
    data object AlreadyTracking : PlannedBlockReminderDecision
    data object Ended : PlannedBlockReminderDecision
}

fun decidePlannedBlockReminder(
    key: String,
    block: ReminderBlock?,
    currentPlannedBlockId: Int?,
    now: Instant,
): PlannedBlockReminderDecision = when {
    block == null || block.reminderKey != key -> PlannedBlockReminderDecision.Stale
    currentPlannedBlockId == block.id -> PlannedBlockReminderDecision.AlreadyTracking
    now >= block.end -> PlannedBlockReminderDecision.Ended
    else -> PlannedBlockReminderDecision.Deliver
}

/** Delivered reminders to remove silently: adopted, ended, or moved/deleted blocks. */
fun plannedBlockRemindersToWithdraw(
    delivered: Set<String>,
    blocks: List<ReminderBlock>,
    currentPlannedBlockId: Int?,
    now: Instant,
): Set<String> {
    val byKey = blocks.associateBy { it.reminderKey }
    return delivered.filterTo(mutableSetOf()) { key ->
        val block = byKey[key]
        block == null || block.id == currentPlannedBlockId || now >= block.end
    }
}

fun plannedBlockReminderTitle(name: String?, taskTitle: String?, taskTypeName: String?): String =
    listOf(name, taskTitle, taskTypeName?.takeUnless { it == "unspecified" })
        .firstOrNull { !it.isNullOrBlank() }?.trim() ?: "Planned Block"

fun plannedBlockReminderText(
    block: ReminderBlock,
    now: Instant,
    zone: ZoneId,
    locale: Locale = Locale.getDefault(),
): String {
    val seconds = Duration.between(now, block.start).seconds
    val lead = when {
        seconds > 0 -> "Starts in ${(seconds + 59) / 60} min"
        seconds > -60 -> "Starting now"
        else -> "Started ${-seconds / 60} min ago"
    }
    val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    return "$lead · ${format.format(block.start)}–${format.format(block.end)}"
}
