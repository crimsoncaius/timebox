# Contextual help coverage — initiative #322

## Inventory contract

For every copy group record platform, surface/source, display condition, original wording/group, remove/help/keep treatment, final wording, help destination or visible placement, implementation and verification. Identify shared consumers and conditional variants separately. Mark reviewed surfaces with no applicable copy explicitly; never equate pending with reviewed. Exclude OS copy, generated answers, user content and development prototypes. Keep consequences, validation, errors, unavailable reasons, useful empty states, partial-result caveats and recovery visible.

## Foundation #323

| Platform / source | Condition and original | Treatment, final wording and destination | Status |
| --- | --- | --- | --- |
| Web `features/activity/CheckInSettings.tsx` | Check in after, enabled or disabled: How long the device must be idle before asking. | Help; unchanged wording, adjacent field trigger | Implemented |
| Android `ui/day/CheckInSettings.kt` | Same field and wording | Help; unchanged, adjacent field trigger | Implemented |
| Web `components/SettingsControls.tsx` SettingsDetails; only consumer `features/activity/CheckInSettings.tsx` | How check-ins work expander: native inactivity; time away insufficient; hidden/frozen/discarded/closed pages; fresh observation; recording unaffected | Help; all three existing paragraphs unchanged; section trigger | Implemented |
| Android `ui/day/CheckInSettings.kt` | Optional check-in details: usage access/screen transitions; leaving app/missing events not inactivity; notification settings; in-app question remains | Help; existing paragraph unchanged; section trigger | Implemented |
| Web `components/TimeBlockInspectorContent.tsx` | Existing non-Google block: Drag the block edges on the timeline to adjust. Edits save as you make them. | Help: Drag the block edges on the timeline to adjust. Keep Edits save as you make them. visible | Implemented |
| Android `ui/day/BlockSheet.kt` | Existing non-Google Planned Block: Drag the block on the timeline to move it, or pull its grooves to resize. | Help; unchanged beside time fields | Implemented |
| Both block editors | Google-linked ownership; time validation; creation/save/discard | Keep current visible copy and behavior | Preserved; finish family in #325 |
| Both check-in settings | Capability/permission status; denied recovery; notification limitations | Keep current visible copy | Preserved; finish family in #324 |

Web saves block edits immediately; Android keeps drafts until Save. Help changes neither. Web measures content for anchored versus centered native dialog; Android measures at current font scale for popover versus scrolling sheet. No help opens automatically. Explicit close and native Back/Escape return to the editor without replacing its state.

## Production family checklist

| Batch | Android surfaces and nested states | Web counterparts | Status |
| --- | --- | --- | --- |
| #324 | Settings, reporting zone, Google setup/selection, Focus/check-ins, notifications, connection, Home, Edit shortcuts, Quick Tracking setup Activity/device instructions, reachable Theme preview | Settings/reporting zone, Google, Focus/check-ins, notifications, API key, navigation; Home shortcut editor, Quick Tracking setup and Theme preview absent | Pending except foundation |
| #325 | Day, planning queue, block editors, Record Actual, start/switch/stop, Plan Now, notes, view options, calendar conflicts, Focus overlay; empty/offline/overlap/validation states | Day/drawers/dialogs/editors, calendar panel, global Focus/check-in hosts | Pending except foundation |
| #326 | Battle Plan, task/subtask editors, Projects selection/order/deletion, filters/Trash, recurring list, scheduled/quota routine subforms and confirmations | Battle Plan, Project/task panels, recurring view/editor | Pending |
| #327 | Chronicle Calendar/Trends, Habits, Time Goals, editors/confirmations, shortened first period warning, empty data | History Calendar/Trends; Habits and Time Goals UI absent | Pending |
| #328 | Task Types management, shared pickers and every consumer, rename/merge/delete resolution, disabled/conflict/empty states | Types management/shared picker/rename/merge/delete | Pending |
| #329 | Conversation, read cards, change confirmations, tracking proposals, activity/task Review, offline/sync/recovery, aggregate scan | Conversation, Review, activity correction; task/tracking confirmation execution absent, Android-directed guidance present | Pending |

## Production source register

Discovery register, not completed copy review. Each batch must inspect conditional branches and shared consumers, record groups above or mark no applicable copy. Debug/prototype sources excluded.

| Platform | Source | Status |
| --- | --- | --- |
| Web | `frontend/src/App.tsx` | Pending family review |
| Web | `frontend/src/components/BlockTimeFields.tsx` | Pending family review |
| Web | `frontend/src/components/CalendarDateField.tsx` | Pending family review |
| Web | `frontend/src/components/ContextualHelp.tsx` | Pending family review |
| Web | `frontend/src/components/DayCalendarPopover.tsx` | Pending family review |
| Web | `frontend/src/components/DayTimeline.tsx` | Pending family review |
| Web | `frontend/src/components/DeleteTaskTypeResolutionModal.tsx` | Pending family review |
| Web | `frontend/src/components/HelperText.tsx` | Pending family review |
| Web | `frontend/src/components/Layout.tsx` | Pending family review |
| Web | `frontend/src/components/ReminderWatcher.tsx` | Pending family review |
| Web | `frontend/src/components/SettingsControls.tsx` | Pending family review |
| Web | `frontend/src/components/TaskTypePathCombobox.tsx` | Pending family review |
| Web | `frontend/src/components/ThemeToggle.tsx` | Pending family review |
| Web | `frontend/src/components/TimeBlockCard.tsx` | Pending family review |
| Web | `frontend/src/components/TimeBlockInspectorContent.tsx` | Pending family review |
| Web | `frontend/src/components/TransientFeedback.tsx` | Pending family review |
| Web | `frontend/src/components/UndoFailureHost.tsx` | Pending family review |
| Web | `frontend/src/components/UndoNotice.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ActivityActualEditor.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ActivitySwitchDialog.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ActivitySwitchUndoHost.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ActivityTimeField.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ActivityTracking.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ActivityTrackingStatus.tsx` | Pending family review |
| Web | `frontend/src/features/activity/BrowserCheckInHost.tsx` | Pending family review |
| Web | `frontend/src/features/activity/CheckInSettings.tsx` | Pending family review |
| Web | `frontend/src/features/activity/FocusMode.tsx` | Pending family review |
| Web | `frontend/src/features/activity/PlanNowEditor.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ReportingDayActuals.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ReportingTimezoneSettings.tsx` | Pending family review |
| Web | `frontend/src/features/activity/SwitchActivityTimeline.tsx` | Pending family review |
| Web | `frontend/src/features/assistant/AssistantPage.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/BattlePlanCard.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/BattlePlanPage.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/BattlePlanSidebar.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/BattlePlanSidebarFrame.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/MoveProjectDialog.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/ProjectEditor.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/RecurringPage.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/SubtaskTitle.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/TaskComposer.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/TaskDetailPanel.tsx` | Pending family review |
| Web | `frontend/src/features/battle-plan/TaskTypeFilterMenu.tsx` | Pending family review |
| Web | `frontend/src/features/calendar/GoogleCalendar.tsx` | Pending family review |
| Web | `frontend/src/features/history/ChronicleHighlight.tsx` | Pending family review |
| Web | `frontend/src/features/history/ChronicleMonthGrid.tsx` | Pending family review |
| Web | `frontend/src/features/history/CustomRangePicker.tsx` | Pending family review |
| Web | `frontend/src/features/history/HistoryPage.tsx` | Pending family review |
| Web | `frontend/src/features/history/TrendsPanel.tsx` | Pending family review |
| Web | `frontend/src/features/home/HomeRedirect.tsx` | Pending family review |
| Web | `frontend/src/features/readiness/ReadinessFailureNotice.tsx` | Pending family review |
| Web | `frontend/src/features/readiness/ReadinessProvider.tsx` | Pending family review |
| Web | `frontend/src/features/settings/SettingsPage.tsx` | Pending family review |
| Web | `frontend/src/features/task-types/RenameTaskTypeSheet.tsx` | Pending family review |
| Web | `frontend/src/features/task-types/TaskTypesPage.tsx` | Pending family review |
| Web | `frontend/src/features/today/DayViewOptions.tsx` | Pending family review |
| Web | `frontend/src/features/today/ReadyToPlanDrawer.tsx` | Pending family review |
| Web | `frontend/src/features/today/RecordingPreview.tsx` | Pending family review |
| Web | `frontend/src/features/today/TodayPage.tsx` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/data/ActivityDay.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/data/ActivityInstant.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/data/ActivityRepository.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/data/remote/ActivityDto.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/MainActivity.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/quicktracking/QuickTrackingActivity.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/AppRoutes.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantCard.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantCards.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantController.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantReviewScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantTaskCards.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantTaskRecovery.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantTasks.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantTransport.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/ProposalTracking.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/TrackingProposal.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/assistant/TrackingProposalCard.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/BattlePlanActions.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/BattlePlanScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/BattlePlanViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/KanbanDragMath.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/MoveProjectMenuItem.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/PlannedDates.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/ProjectNameSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/ProjectNavigationList.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/ProjectOrderDialog.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/ProjectSelectionMotion.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RecurringCreationScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RecurringEditorViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RecurringScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RecurringViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RoutineCalendarSection.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RoutineDraftSaver.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RoutinePreplanningFields.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RoutineRepeatEditor.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/RoutineScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/TaskComposerSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/TaskDetailViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/TaskFieldsSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/TaskReminder.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/TaskSubtasks.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/TaskTypeFilter.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/battleplan/TaskTypeFilterSection.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/calendar/CalendarSelectionList.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/calendar/GoogleCalendarPanel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/calendar/GoogleCalendarSettings.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/ChronicleScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/ChronicleViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/CustomRangeSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/HabitEdits.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/HabitsScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/HabitsViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/TimeGoalDetails.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/TimeGoalEditor.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/TimeGoalsContent.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/TimeGoalsScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/TimeGoalsViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/chronicle/TrendsScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/Chrome.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/Common.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/ContextualHelp.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/CurrentRangeAction.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/NavigationDirectory.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/NavigationShortcuts.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/TimeboxSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/TransientFeedback.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/WheelDatePicker.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ActivityActualEditor.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ActivitySelectionFields.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ActivityStatus.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ActivityTimeField.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ActivityTimeMetrics.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ActivityTracking.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ActivityTrackingStatus.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/BlockDuration.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/BlockEmphasis.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/BlockPlacement.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/BlockSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/BlockTimeFields.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/BlockTimeInteraction.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/CheckInSettings.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ClipInPlace.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/CurrentActivityControl.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/CurrentActivityNotesSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/DayCalendarHeader.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/DayCalendarHeaderLogic.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/DayLoadProblem.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/DayScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/DayTimeline.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/DayViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/DayViewOptionsDialog.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/LogTimeForm.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/LongPressArmedDragGesture.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/PlanMode.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/PlannedBlockSuggestion.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/PlannedRecordingState.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/RecordingPreview.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/RecordingPreviewStudy.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/RecordingTimeline.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/RecordingTimelineModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/ReportingDayActuals.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/RunningActualSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/StartTrackingSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/StopTrackingSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/SwitchActivitySheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/SwitchActivityTimeline.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/SwitchTimelineModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/TaskTypePicker.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/TaskTypeRecommendation.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/TimelineZoom.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/TrackingHandoff.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/day/UntrackedGaps.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/ElapsedDuration.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/focus/FocusController.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/focus/FocusMode.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/MondayDatePicker.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/planning/PlanningSession.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/readiness/ReadyToPlanCoordinator.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/readiness/ReadyToPlanFailureNotice.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/settings/SettingsScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/settings/SettingsViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/ShortcutSlide.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/taskcompletion/TaskCompletion.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/theme/Colors.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/theme/Theme.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/theme/ThemePreviewScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/theme/Type.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/TimeboxApp.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/TimeboxNavHost.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/TimeFormat.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/types/RenameTaskTypeSheet.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/types/TypesScreen.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/types/TypesViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/undo/UndoLifecycle.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/undo/UndoNoticeHost.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/ViewModelFactory.kt` | Pending family review |

## Foundation validation and review (2026-10-26)

- Web production build and focused ESLint passed. Vitest: 17/17 passing across ContextualHelp and TimeBlockInspectorContent. Tests cover initially hidden help, keyboard activation, explicit dismissal, one open help panel, focus return and draft preservation without form submission.
- Real Chromium: short anchored help, long dialog, Escape/focus return, native accessibility tree names/order, dark/light rendering, 360px viewport at 200% text, no horizontal help overflow. Existing block-time draft (10:15) and enabled Save times survived help and Escape on both desktop and narrow nested editor. No screen-reader audio session was performed.
- Android assembleDebug and instrumentation compilation passed. ContextualHelpTest: 2/2 passing on managed Android 36, exercising short popover and long sheet inside a Dialog with an unsaved text draft. Full instrumentation suite not run; known baseline remains unchanged. Real Settings help checked with touch/Back, normal light font and 200% dark font. Long content and explicit close remained readable. TalkBack audio and physical-device testing were not performed.
- Emulator startup briefly reported System UI not responding; it recovered before review verification. Initial Gradle property invocation needed PowerShell quoting; corrected focused run passed. An initial ESLint invocation from repo root was corrected to frontend cwd and passed.
- Review: web http://127.0.0.1:12107/settings; API http://127.0.0.1:12106 with isolated SQLite C:/Users/Caius/TimeboxRuntime/contextual-help-322.db. Vite and Uvicorn run from this worktree; Uvicorn uses the existing primary checkout Python environment only as its interpreter.
- Android review: emulator-5606, token f1c680cd89aa47e591ea6c965c823af6, owner initiative-322 contextual help review, storage root C:/Users/Caius/TimeboxRuntime/emulators. App installed from updated tree with -PreviewApiBaseUrl=http://10.0.2.2:12106/. Normal text size restored, light Settings at Focus & check-ins; help dismissed. Resume this reservation for later batches, then return it to review. Preserve unrelated devices.
- Screenshots retained outside Git at C:/Users/Caius/TimeboxRuntime/help322-web-light.png, help322-web-nested.png, help322-android-short-final.png and help322-android-dark-large.png. Early Android screenshots without the -final suffix captured before popup animation settled and are not validation evidence.
- Remaining source-register rows deliberately pending for #324â€“329. These batches must classify each group and explicitly mark reviewed no-copy surfaces; no family is certified complete by this foundation.
