package com.timebox.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timebox.android.data.Lane
import com.timebox.android.ui.chronicle.ChronicleScreen
import com.timebox.android.ui.chronicle.ChronicleViewModel
import com.timebox.android.ui.components.TimeboxBottomNav
import com.timebox.android.ui.components.TimeboxTab
import com.timebox.android.ui.components.TimeboxTopBar
import com.timebox.android.ui.day.DayScreen
import com.timebox.android.ui.day.DayViewModel
import com.timebox.android.ui.review.ReviewScreen
import com.timebox.android.ui.review.ReviewViewModel
import com.timebox.android.ui.settings.SettingsScreen
import com.timebox.android.ui.settings.SettingsViewModel
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.types.TypesScreen
import com.timebox.android.ui.types.TypesViewModel

/** Review is reached from the Day tab rather than the nav bar, as in the design. */
private enum class Screen { Day, Chronicle, Types, Settings, Review }

@Composable
fun TimeboxApp(
    isDark: Boolean,
    onToggleDark: () -> Unit,
) {
    val repository = rememberRepository()
    val factory = remember(repository) { timeboxViewModelFactory(repository) }

    val dayViewModel: DayViewModel = viewModel(factory = factory)
    val chronicleViewModel: ChronicleViewModel = viewModel(factory = factory)
    val typesViewModel: TypesViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val reviewViewModel: ReviewViewModel = viewModel(factory = factory)

    val dayState by dayViewModel.state.collectAsState()
    val chronicleState by chronicleViewModel.state.collectAsState()
    val typesState by typesViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val reviewState by reviewViewModel.state.collectAsState()

    var screen by remember { mutableStateOf(Screen.Day) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(screen) {
        when (screen) {
            Screen.Chronicle -> {
                dayState.day?.today?.let(chronicleViewModel::setToday)
                chronicleViewModel.load()
            }
            Screen.Types -> typesViewModel.load()
            Screen.Settings -> settingsViewModel.load(dayState.day?.timezone)
            Screen.Review -> reviewViewModel.load(dayState.date)
            Screen.Day -> {
                // Types may have changed on the Types tab; the chips must stay current.
                dayViewModel.start()
                dayViewModel.refreshTaskTypes()
            }
        }
    }

    // Surface one-off outcomes (saved, deleted, rejected) without blocking the screen.
    LaunchedEffect(dayState.message) {
        dayState.message?.let {
            snackbarHostState.showSnackbar(it)
            dayViewModel.consumeMessage()
        }
    }
    LaunchedEffect(typesState.message) {
        typesState.message?.let {
            snackbarHostState.showSnackbar(it)
            typesViewModel.consumeMessage()
        }
    }
    LaunchedEffect(settingsState.message) {
        settingsState.message?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.consumeMessage()
        }
    }

    val colors = TimeboxTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            TimeboxTopBar(
                kicker = when (screen) {
                    Screen.Day -> "Day"
                    Screen.Chronicle -> "Chronicle"
                    Screen.Types -> "Task types"
                    Screen.Settings -> "Settings"
                    Screen.Review -> "Review"
                },
                title = when (screen) {
                    Screen.Day -> formatFullDate(dayState.date)
                    Screen.Chronicle -> formatMonthTitle(chronicleState.monthStart)
                    Screen.Types -> "Paths"
                    Screen.Settings -> "Preferences"
                    Screen.Review -> formatShortDate(reviewState.date)
                },
                isDark = isDark,
                onToggleTheme = onToggleDark,
                onOpenReview = if (screen == Screen.Day) {
                    { screen = Screen.Review }
                } else {
                    null
                },
            )

            Box(modifier = Modifier.weight(1f)) {
                when (screen) {
                    Screen.Day -> DayScreen(
                        state = dayState,
                        onPrevDay = { dayViewModel.shiftDay(-1) },
                        onNextDay = { dayViewModel.shiftDay(1) },
                        onRetry = { dayViewModel.start() },
                        onTapSlot = { lane: Lane, minute: Int ->
                            dayViewModel.startDraft(lane, minute)
                        },
                        onSelectBlock = dayViewModel::selectBlock,
                        onCommitMove = dayViewModel::moveBlock,
                        onDismissSheet = dayViewModel::closeSheet,
                        onChooseType = dayViewModel::chooseTaskType,
                        onTypeQueryChange = dayViewModel::onTypeQueryChange,
                        onCreateType = dayViewModel::createTaskTypeAndChoose,
                        onNoteChange = dayViewModel::onNoteChange,
                        onDeleteSelected = dayViewModel::deleteSelected,
                        onCompleteSelected = dayViewModel::completeSelected,
                    )

                    Screen.Chronicle -> ChronicleScreen(
                        state = chronicleState,
                        onPrevMonth = { chronicleViewModel.shiftMonth(-1) },
                        onNextMonth = { chronicleViewModel.shiftMonth(1) },
                        onThisMonth = chronicleViewModel::goToThisMonth,
                        onOpenDay = { date ->
                            dayViewModel.goToDate(date)
                            screen = Screen.Day
                        },
                        onRetry = chronicleViewModel::load,
                    )

                    Screen.Types -> TypesScreen(
                        state = typesState,
                        onInputChange = typesViewModel::onInputChange,
                        onAdd = typesViewModel::addType,
                        onDelete = typesViewModel::deleteType,
                        onConfirmCascade = typesViewModel::confirmCascadeDelete,
                        onDismissCascade = typesViewModel::dismissCascadePrompt,
                        onRetry = typesViewModel::load,
                    )

                    Screen.Settings -> SettingsScreen(
                        state = settingsState,
                        isDark = isDark,
                        onToggleDark = onToggleDark,
                        onStartHourDelta = settingsViewModel::adjustStartHour,
                        onEndHourDelta = settingsViewModel::adjustEndHour,
                        onToggleFullDay = settingsViewModel::toggleFullDay,
                        onBaseUrlChange = settingsViewModel::onBaseUrlChange,
                        onApiKeyChange = settingsViewModel::onApiKeyChange,
                        onSaveConnection = {
                            settingsViewModel.saveConnection()
                            dayViewModel.load(showSpinner = true)
                        },
                        onRetry = { settingsViewModel.load(dayState.day?.timezone) },
                    )

                    Screen.Review -> ReviewScreen(
                        state = reviewState,
                        onBackToDay = { screen = Screen.Day },
                        onRetry = { reviewViewModel.load(dayState.date) },
                    )
                }
            }

            TimeboxBottomNav(
                selected = when (screen) {
                    // Review belongs to the Day tab, so the pill stays there.
                    Screen.Day, Screen.Review -> TimeboxTab.Day
                    Screen.Chronicle -> TimeboxTab.Chronicle
                    Screen.Types -> TimeboxTab.Types
                    Screen.Settings -> TimeboxTab.Settings
                },
                onSelect = { tab ->
                    screen = when (tab) {
                        TimeboxTab.Day -> Screen.Day
                        TimeboxTab.Chronicle -> Screen.Chronicle
                        TimeboxTab.Types -> Screen.Types
                        TimeboxTab.Settings -> Screen.Settings
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
        ) { data ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .background(colors.on, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Text(
                    text = data.visuals.message,
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.bg,
                )
            }
        }
    }
}
