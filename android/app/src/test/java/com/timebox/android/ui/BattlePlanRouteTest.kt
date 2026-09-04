package com.timebox.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePlanRouteTest {
    @Test
    fun `Trash Undo is preserved across every Battle Plan surface`() {
        listOf(
            AppRoutes.BattlePlan,
            AppRoutes.TaskDetailPattern,
            AppRoutes.ProjectNew,
            AppRoutes.ProjectDetailPattern,
            AppRoutes.Recurring,
            AppRoutes.RecurringNew,
            AppRoutes.RecurringDetailPattern,
            AppRoutes.RecurringEditPattern,
        ).forEach { route -> assertTrue(route, isBattlePlanRoute(route)) }
    }

    @Test
    fun `Trash Undo is discarded outside Battle Plan`() {
        listOf(
            AppRoutes.DayPattern,
            AppRoutes.Chronicle,
            AppRoutes.Types,
            AppRoutes.Settings,
        ).forEach { route -> assertFalse(route, isBattlePlanRoute(route)) }
    }
}
