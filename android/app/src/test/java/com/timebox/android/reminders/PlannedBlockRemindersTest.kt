package com.timebox.android.reminders

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedBlockRemindersTest {
    private fun at(time: String) = Instant.parse("2026-09-19T${time}:00Z")
    private fun block(id: Int, start: String, end: String, title: String = "Deep work") = ReminderBlock(id, at(start), at(end), title)
    private val on = PlannedBlockReminderSettings(enabled = true, leadMinutes = 5)

    @Test fun remindsLeadMinutesBeforeStart() {
        val plan = planPlannedBlockReminders(listOf(block(1, "09:00", "10:00")), on, emptySet(), emptySet(), at("08:30"))
        assertEquals(listOf(PlannedBlockAlarm("1@${at("09:00")}", 1, at("08:55"))), plan.alarms)
    }

    @Test fun atStartLeadFiresAtTheStart() {
        val plan = planPlannedBlockReminders(listOf(block(1, "09:00", "10:00")), on.copy(leadMinutes = 0), emptySet(), emptySet(), at("08:30"))
        assertEquals(at("09:00"), plan.alarms.single().fireAt)
    }

    @Test fun disabledSchedulesNothing() {
        val plan = planPlannedBlockReminders(listOf(block(1, "09:00", "10:00")), on.copy(enabled = false), emptySet(), emptySet(), at("08:30"))
        assertTrue(plan.alarms.isEmpty() && plan.skipped.isEmpty())
    }

    @Test fun lateDiscoveryFiresNowBeforeStartAndSkipsAfterStart() {
        val standup = block(1, "08:56", "09:11", "Standup")
        val review = block(2, "09:03", "09:33", "Review PR")
        val plan = planPlannedBlockReminders(listOf(standup, review), on, emptySet(), emptySet(), at("09:00"))
        assertEquals(setOf(standup.reminderKey), plan.skipped)
        assertEquals(listOf(PlannedBlockAlarm(review.reminderKey, 2, at("09:00"))), plan.alarms)
    }

    @Test fun alreadyScheduledReminderSurvivesALateReplan() {
        val deepWork = block(1, "09:00", "10:00")
        val plan = planPlannedBlockReminders(listOf(deepWork), on.copy(leadMinutes = 0), emptySet(), setOf(deepWork.reminderKey), at("09:04"))
        assertEquals(at("09:04"), plan.alarms.single().fireAt)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test fun handledBlockIsRemindedOnceButMovingItRearms() {
        val original = block(1, "09:00", "10:00")
        val handled = setOf(original.reminderKey)
        assertTrue(planPlannedBlockReminders(listOf(original), on, handled, emptySet(), at("08:56")).alarms.isEmpty())
        val moved = block(1, "10:00", "11:00")
        assertEquals(at("09:55"), planPlannedBlockReminders(listOf(moved), on, handled, emptySet(), at("08:56")).alarms.single().fireAt)
    }

    @Test fun decisionSkipsAdoptedEndedAndStaleBlocks() {
        val deepWork = block(1, "09:00", "10:00")
        val key = deepWork.reminderKey
        assertEquals(PlannedBlockReminderDecision.Deliver, decidePlannedBlockReminder(key, deepWork, null, at("08:55")))
        assertEquals(PlannedBlockReminderDecision.Deliver, decidePlannedBlockReminder(key, deepWork, 7, at("08:55")))
        assertEquals(PlannedBlockReminderDecision.AlreadyTracking, decidePlannedBlockReminder(key, deepWork, 1, at("08:55")))
        assertEquals(PlannedBlockReminderDecision.Ended, decidePlannedBlockReminder(key, deepWork, null, at("10:00")))
        assertEquals(PlannedBlockReminderDecision.Stale, decidePlannedBlockReminder(key, null, null, at("08:55")))
        assertEquals(PlannedBlockReminderDecision.Stale, decidePlannedBlockReminder(key, block(1, "10:00", "11:00"), null, at("08:55")))
    }

    @Test fun withdrawsAdoptedEndedMovedAndDeletedReminders() {
        val adopted = block(1, "09:00", "10:00")
        val ended = block(2, "08:00", "08:59")
        val waiting = block(3, "09:30", "10:00")
        val movedFrom = block(4, "09:00", "09:30")
        val deleted = block(5, "09:00", "09:30")
        val delivered = setOf(adopted, ended, waiting, movedFrom, deleted).map { it.reminderKey }.toSet()
        val withdraw = plannedBlockRemindersToWithdraw(delivered, listOf(adopted, ended, waiting, block(4, "11:00", "11:30")), 1, at("09:01"))
        assertEquals(setOf(adopted, ended, movedFrom, deleted).map { it.reminderKey }.toSet(), withdraw)
    }

    @Test fun titleFallsBackFromBlockNameToTaskToTypeToPlannedBlock() {
        assertEquals("Chapter one", plannedBlockReminderTitle("Chapter one", "Draft", "Writing"))
        assertEquals("Draft", plannedBlockReminderTitle(" ", "Draft", "Writing"))
        assertEquals("Writing", plannedBlockReminderTitle(null, null, "Writing"))
        assertEquals("Planned Block", plannedBlockReminderTitle(null, null, "unspecified"))
    }

    @Test fun textDescribesTimeUntilStart() {
        val deepWork = block(1, "09:00", "10:30")
        val utc = ZoneId.of("UTC")
        assertEquals("Starts in 5 min · 09:00–10:30", plannedBlockReminderText(deepWork, at("08:55"), utc, Locale.UK))
        assertEquals("Starting now · 09:00–10:30", plannedBlockReminderText(deepWork, at("09:00"), utc, Locale.UK))
        assertEquals("Started 6 min ago · 09:00–10:30", plannedBlockReminderText(deepWork, at("09:06"), utc, Locale.UK))
    }
}
