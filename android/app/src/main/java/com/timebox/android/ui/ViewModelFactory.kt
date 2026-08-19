package com.timebox.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.ui.chronicle.ChronicleViewModel
import com.timebox.android.ui.battleplan.BattlePlanViewModel
import com.timebox.android.ui.battleplan.ProjectEditorViewModel
import com.timebox.android.ui.battleplan.RecurringEditorViewModel
import com.timebox.android.ui.battleplan.RecurringViewModel
import com.timebox.android.ui.battleplan.TaskDetailViewModel
import com.timebox.android.ui.day.DayViewModel
import com.timebox.android.ui.review.ReviewViewModel
import com.timebox.android.ui.settings.SettingsViewModel
import com.timebox.android.ui.types.TypesViewModel

@Composable
fun rememberRepository(): TimeboxRepository {
    val context = LocalContext.current
    return (context.applicationContext as TimeboxApplication).repository
}

/** One factory for every screen; each initializer only fires for its own class. */
fun timeboxViewModelFactory(repository: TimeboxRepository): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { DayViewModel(repository) }
        initializer { ChronicleViewModel(repository) }
        initializer { TypesViewModel(repository) }
        initializer { SettingsViewModel(repository) }
        initializer { ReviewViewModel(repository) }
        initializer { BattlePlanViewModel(repository) }
        initializer { TaskDetailViewModel(repository) }
        initializer { ProjectEditorViewModel(repository) }
        initializer { RecurringViewModel(repository) }
        initializer { RecurringEditorViewModel(repository) }
    }
