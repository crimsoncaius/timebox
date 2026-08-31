package com.timebox.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AppRoutesTest {
    @Test
    fun `existing destinations retain stable route builders`() {
        assertEquals("day/2026-08-17", AppRoutes.day(LocalDate.parse("2026-08-17")))
        assertEquals("chronicle", AppRoutes.Chronicle)
        assertEquals("types", AppRoutes.Types)
        assertEquals("settings", AppRoutes.Settings)
        assertEquals("settings/theme-preview", AppRoutes.ThemePreview)
        assertEquals("Theme preview", routeTitle(AppRoutes.ThemePreview, "Day", "Month"))
    }

    @Test
    fun `task route and deep link preserve task id`() {
        assertEquals("battle-plan/task/42", AppRoutes.taskDetail(42))
        assertEquals("timebox://battle-plan/task/42", AppRoutes.taskDeepLink(42))
        assertTrue(AppRoutes.TaskDetailPattern.contains("{${AppRoutes.TaskIdArg}}"))
        assertEquals(
            "Task details",
            routeTitle(AppRoutes.TaskDetailPattern, "Day", "Month"),
        )
    }

    @Test
    fun `recurring routes preserve template id and separate details from editing`() {
        assertEquals("battle-plan/recurring", AppRoutes.Recurring)
        assertEquals("battle-plan/recurring/new", AppRoutes.RecurringNew)
        assertEquals("battle-plan/recurring/7", AppRoutes.recurringDetail(7))
        assertEquals("battle-plan/recurring/7/edit", AppRoutes.recurringEdit(7))
        assertTrue(AppRoutes.RecurringDetailPattern.contains("{${AppRoutes.TemplateIdArg}}"))
        assertTrue(AppRoutes.RecurringEditPattern.contains("{${AppRoutes.TemplateIdArg}}"))
    }
}
