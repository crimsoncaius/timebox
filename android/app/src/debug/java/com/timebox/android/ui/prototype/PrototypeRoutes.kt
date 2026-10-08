package com.timebox.android.ui.prototype

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.timebox.android.ui.battleplan.RecurringDetailsHierarchyPrototype
import com.timebox.android.ui.battleplan.RoutineSheetPrototype
import com.timebox.android.ui.battleplan.TaskSheetPrototype
import com.timebox.android.ui.chronicle.HabitsPrototype

/**
 * Design-study destinations, reachable by deep link in debug builds only.
 *
 * The release source set supplies a no-op of the same name, so neither these
 * routes nor the screens behind them are compiled into a release APK.
 */
fun NavGraphBuilder.prototypeRoutes() {
    composable(
        "prototype/assistant-polish?layout={layout}&scenario={scenario}",
        deepLinks = listOf(navDeepLink { uriPattern = "timebox://prototype/assistant-polish?layout={layout}&scenario={scenario}" }),
        arguments = listOf(
            navArgument("layout") { defaultValue = "focused" },
            navArgument("scenario") { defaultValue = "complete" },
        ),
    ) { entry ->
        com.timebox.android.ui.assistant.AssistantPolishPrototype(
            entry.arguments?.getString("layout") ?: "focused",
            entry.arguments?.getString("scenario") ?: "complete",
        )
    }
    composable(
        "prototype/goal-archive?variant={variant}",
        deepLinks = listOf(navDeepLink { uriPattern = "timebox://prototype/goal-archive?variant={variant}" }),
        arguments = listOf(navArgument("variant") { defaultValue = "page" }),
    ) { entry ->
        com.timebox.android.ui.chronicle.GoalArchivePrototype(entry.arguments?.getString("variant") ?: "page")
    }
    composable(
        "prototype/task-sheet?mode={mode}&layout={layout}&sample={sample}",
        deepLinks = listOf(navDeepLink { uriPattern = "timebox://prototype/task-sheet?mode={mode}&layout={layout}&sample={sample}" }),
        arguments = listOf(
            navArgument("mode") { defaultValue = "details" },
            navArgument("layout") { defaultValue = "full" },
            navArgument("sample") { defaultValue = "normal" },
        ),
    ) { entry ->
        TaskSheetPrototype(
            entry.arguments?.getString("mode") == "create",
            entry.arguments?.getString("layout") ?: "full",
            entry.arguments?.getString("sample") ?: "normal",
        )
    }
    composable(
        "prototype/recurring-details?flow={flow}&layout={layout}&mode={mode}",
        deepLinks = listOf(navDeepLink { uriPattern = "timebox://prototype/recurring-details?flow={flow}&layout={layout}&mode={mode}" }),
        arguments = listOf(
            navArgument("flow") { defaultValue = "edit" },
            navArgument("layout") { defaultValue = "rows" },
            navArgument("mode") { defaultValue = "scheduled" },
        ),
    ) { entry ->
        if (entry.arguments?.getString("layout") == "routine") {
            RoutineSheetPrototype(
                entry.arguments?.getString("flow") ?: "details",
                entry.arguments?.getString("mode") ?: "scheduled",
            )
        } else {
            RecurringDetailsHierarchyPrototype(
                initialFlow = entry.arguments?.getString("flow") ?: "edit",
                initialScenario = entry.arguments?.getString("mode") ?: "scheduled",
            )
        }
    }
    composable(
        "prototype/habits?total={total}&scenario={scenario}",
        deepLinks = listOf(navDeepLink { uriPattern = "timebox://prototype/habits?total={total}&scenario={scenario}" }),
        arguments = listOf(
            navArgument("total") { defaultValue = "labeled" },
            navArgument("scenario") { defaultValue = "sample" },
        ),
    ) { entry ->
        HabitsPrototype(
            entry.arguments?.getString("total") ?: "labeled",
            entry.arguments?.getString("scenario") ?: "sample",
        )
    }
}
