package com.timebox.android.ui.chronicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TimeGoalsScreen(viewModel: TimeGoalsViewModel, state: TimeGoalsUiState) {
    LaunchedEffect(viewModel) {
        viewModel.refresh()
        while (true) { delay(60_000); viewModel.refresh() }
    }
    val savedScreens = rememberSaveableStateHolder()
    if (state.archiveOpen) {
        savedScreens.SaveableStateProvider("archive") { TimeGoalArchiveContent(state, viewModel) }
        return
    }
    Column(Modifier.fillMaxSize()) {
        TextButton(viewModel::openArchive, Modifier.align(Alignment.End).padding(end = TimeboxDimens.screenPadding), enabled = !state.goalSaving) { Text("Archive") }
        savedScreens.SaveableStateProvider("week") { TimeGoalsWeekContent(viewModel, state) }
    }
}

@Composable
private fun TimeGoalsWeekContent(viewModel: TimeGoalsViewModel, state: TimeGoalsUiState) {
    val report = state.goals
    if (report == null) {
        if (state.error != null) ErrorState(state.error, viewModel::refresh, Modifier.fillMaxSize())
        else LoadingState(Modifier.fillMaxSize())
        return
    }
    val colors = TimeboxTheme.colors
    val currentWeek = report.today.minusDays((report.today.dayOfWeek.value - 1).toLong())
    val canNavigate = !state.offline && !state.goalSaving && !state.loading
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton({ viewModel.shiftWeek(-1) }, enabled = canNavigate && report.weekStart > report.earliestWeekStart) {
                Icon(Icons.Outlined.ChevronLeft, "Previous week")
            }
            TextButton(viewModel::thisWeek, Modifier.weight(1f), enabled = canNavigate) {
                Text(if (report.weekStart == currentWeek) "This week"
                    else "${report.weekStart.format(formatter)} – ${report.weekStart.plusDays(6).format(formatter)}",
                    style = TimeboxTheme.type.sectionTitle, color = colors.on)
            }
            IconButton({ viewModel.shiftWeek(1) }, enabled = canNavigate && report.weekStart < currentWeek) {
                Icon(Icons.Outlined.ChevronRight, "Next week")
            }
        }
        (state.error ?: if (state.offline) "Offline" else null)?.let { message ->
            Row(Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding), verticalAlignment = Alignment.CenterVertically) {
                val updated = report.capturedAt.atZone(report.timezone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
                Text(if (state.offline) "Offline · last updated $updated\nUnsynced tracked time is not included." else message,
                    Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                TextButton(viewModel::refresh) { Text("Retry") }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(top = 12.dp, bottom = TimeboxDimens.bottomInset).testTag("time-goals-week")) {
            TimeGoalsContent(state, viewModel)
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
        }
    }
}
