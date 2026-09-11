package com.timebox.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.ui.chronicle.ChronicleViewModel
import com.timebox.android.ui.battleplan.BattlePlanViewModel
import com.timebox.android.ui.battleplan.RecurringEditorViewModel
import com.timebox.android.ui.battleplan.RecurringViewModel
import com.timebox.android.ui.battleplan.TaskDetailViewModel
import com.timebox.android.ui.day.DayViewModel
import com.timebox.android.ui.planning.PlanningSession
import com.timebox.android.ui.planning.RepositoryPlanningSessionTransport
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import com.timebox.android.ui.settings.SettingsViewModel
import com.timebox.android.ui.taskcompletion.TaskCompletion
import com.timebox.android.ui.types.TypesViewModel

@Composable
fun rememberRepository(): TimeboxRepository {
    val context = LocalContext.current
    return (context.applicationContext as TimeboxApplication).repository
}

@Composable
fun rememberTaskCompletion(): TaskCompletion {
    val context = LocalContext.current
    return (context.applicationContext as TimeboxApplication).taskCompletion
}

/** One factory for every screen; each initializer only fires for its own class. */
fun timeboxViewModelFactory(
    repository: TimeboxRepository,
    taskCompletion: TaskCompletion,
    readinessCoordinator: ReadyToPlanCoordinator,
    activityRepository: com.timebox.android.data.ActivityRepository? = null,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DayViewModel(
                repository,
                taskCompletion = taskCompletion,
                planningSession = PlanningSession(
                    RepositoryPlanningSessionTransport(repository, readinessCoordinator),
                ),
                readinessCoordinator = readinessCoordinator,
                activityRepository = activityRepository,
            )
        }
        initializer { ChronicleViewModel(repository) }
        initializer { TypesViewModel(repository) }
        initializer { SettingsViewModel(repository) }
        initializer {
            BattlePlanViewModel(
                repository,
                taskCompletion,
                createSavedStateHandle(),
                readinessCoordinator = readinessCoordinator,
            )
        }
        initializer {
            TaskDetailViewModel(
                repository,
                taskCompletion,
                createSavedStateHandle(),
                readinessCoordinator,
            )
        }
        initializer { RecurringViewModel(repository) }
        initializer { RecurringEditorViewModel(repository) }
    }
