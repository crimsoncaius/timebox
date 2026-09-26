package com.timebox.android.ui.prototype

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.timebox.android.ui.battleplan.RecurringDetailsHierarchyPrototype
import com.timebox.android.ui.battleplan.RoutineSheetPrototype
import com.timebox.android.ui.battleplan.TaskSheetPrototype
import com.timebox.android.ui.battleplan.TaskTypeFilterPrototype

/**
 * Design-study destinations, reachable by deep link in debug builds only.
 *
 * The release source set supplies a no-op of the same name, so neither these
 * routes nor the screens behind them are compiled into a release APK.
 */
fun NavGraphBuilder.prototypeRoutes() {
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
        "prototype/type-filter?variant={variant}&data={data}",
        deepLinks = listOf(navDeepLink { uriPattern = "timebox://prototype/type-filter?variant={variant}&data={data}" }),
        arguments = listOf(
            navArgument("variant") { defaultValue = "inline" },
            navArgument("data") { defaultValue = "many" },
        ),
    ) { entry ->
        TaskTypeFilterPrototype(
            entry.arguments?.getString("variant") ?: "inline",
            entry.arguments?.getString("data") ?: "many",
        )
    }
}
