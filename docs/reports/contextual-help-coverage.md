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
| #324 | Settings, reporting zone, Google setup/selection, Focus/check-ins, notifications, connection, Home, Edit shortcuts, Quick Tracking setup Activity/device instructions, reachable Theme preview | Settings/reporting zone, Google, Focus/check-ins, notifications, API key, navigation; Home shortcut editor, Quick Tracking setup and Theme preview absent | Complete #324; detailed groups below |
| #325 | Day, planning queue, block editors, Record Actual, start/switch/stop, Plan Now, notes, view options, calendar conflicts, Focus overlay; empty/offline/overlap/validation states | Day/drawers/dialogs/editors, calendar panel, global Focus/check-in hosts | Complete #325; detailed groups below |
| #326 | Battle Plan, task/subtask editors, Projects selection/order/deletion, filters/Trash, recurring list, scheduled/quota routine subforms and confirmations | Battle Plan, Project/task panels, recurring view/editor | Complete #326; detailed groups below |
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
| Web | `frontend/src/components/Layout.tsx` | Reviewed #324; groups below |
| Web | `frontend/src/components/ReminderWatcher.tsx` | Pending family review |
| Web | `frontend/src/components/SettingsControls.tsx` | Pending family review |
| Web | `frontend/src/components/TaskTypePathCombobox.tsx` | Pending family review |
| Web | `frontend/src/components/ThemeToggle.tsx` | Reviewed #324; groups below |
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
| Web | `frontend/src/features/activity/CheckInSettings.tsx` | Reviewed #324; groups below |
| Web | `frontend/src/features/activity/FocusMode.tsx` | Pending family review |
| Web | `frontend/src/features/activity/PlanNowEditor.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ReportingDayActuals.tsx` | Pending family review |
| Web | `frontend/src/features/activity/ReportingTimezoneSettings.tsx` | Reviewed #324; groups below |
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
| Web | `frontend/src/features/settings/SettingsPage.tsx` | Reviewed #324; groups below |
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
| Android | `android/app/src/main/java/com/timebox/android/ui/components/NavigationDirectory.kt` | Reviewed #324; groups below |
| Android | `android/app/src/main/java/com/timebox/android/ui/components/NavigationShortcuts.kt` | Reviewed #324; groups below |
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
| Android | `android/app/src/main/java/com/timebox/android/ui/settings/SettingsScreen.kt` | Reviewed #324; groups below |
| Android | `android/app/src/main/java/com/timebox/android/ui/settings/SettingsViewModel.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/ShortcutSlide.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/taskcompletion/TaskCompletion.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/theme/Colors.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/theme/Theme.kt` | Pending family review |
| Android | `android/app/src/main/java/com/timebox/android/ui/theme/ThemePreviewScreen.kt` | Reviewed #324; groups below |
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
- Remaining source-register rows deliberately pending for #324–329. These batches must classify each group and explicitly mark reviewed no-copy surfaces; no family is certified complete by this foundation.

## Settings, integrations and navigation #324

Paths are relative to `frontend/src/` (web) or `android/app/src/main/java/com/timebox/android/` (Android). Every row includes source review of the stated conditions. Unchanged means original wording is final wording. Live checks are representative; no claim is made that every OS permission or Google account state was exercised live.

| Platform / source / surface | Condition and original wording or group | Decision; final wording and placement | Implementation / verification |
| --- | --- | --- | --- |
| Web `features/settings/SettingsPage.tsx` | Always: Shared settings apply on every device. The rest stay on this one.; How daily totals are grouped. | Remove introductions; All devices / This device badges remain. | Implemented; browser light/dark/narrow |
| Android `ui/settings/SettingsScreen.kt` | Day & time intro How daily totals are grouped. | Remove; All devices remains. | Implemented; settings test |
| Web `features/activity/ReportingTimezoneSettings.tsx` | Daily grouping, travel independence, unchanged recorded times and durations. | Help Reporting Time Zone: Daily totals use this zone. Travel does not change it. Keep visible Changes daily totals on all devices; recorded times and durations stay the same. | Implemented; draft/Escape browser check; form tests |
| Android `SettingsScreen.kt`, ReportingZoneRow | Current value plus daily grouping/travel rule. | Keep current zone / Not set; help: Daily totals use this shared zone. Travel does not change it. | Implemented; selection/save test |
| Android same, ReportingZoneDialog | Shared by all devices. Changing it regroups daily totals; recorded times and elapsed duration stay the same. | Keep shortened consequence: Changes daily totals on all devices; recorded times and elapsed duration stay the same. | Implemented; nested selection/save test |
| Both reporting-zone editors, above sources | Web unknown zone/server error; Android no search matches; selection/save. | Keep validation/recovery, No matching time zones and selected values unchanged. | Source review; form tests |
| Web `features/calendar/GoogleCalendar.tsx` | Settings only, Import commitments into your plan. Google Calendar is never changed. | Help Google Calendar; unchanged wording. | Implemented; live unconfigured state |
| Android `SettingsScreen.kt` | Bring calendar commitments into your plan. Google events stay unchanged. | Help beside Google Calendar heading; unchanged wording. | Implemented |
| Web `GoogleCalendar.tsx` | Connected: The first selected copy supplies a duplicated meeting’s details. | Help Calendar priority: original rule plus Use Move up to change priority. Keep priority-order legend, selections and buttons. | Implemented; calendar regression test |
| Android `ui/calendar/CalendarSelectionList.kt` | Selected calendars: Hold and drag to change priority. The first calendar supplies details when a meeting appears more than once. | Help Calendar priority: same rules plus Move up and Move down accessibility actions. First choice stays visible. | Implemented; drag and draft/help test |
| Android same | Zero selected / zero available calendars. | Keep Select a calendar below to start importing.; No calendars are available yet. Try syncing again, or check this account in Google Calendar. | Source-reviewed useful empty/recovery states |
| Android `ui/calendar/GoogleCalendarSettings.kt` | Loading; load failure with/without retained snapshot; unconfigured; awaiting Google; first connection; disconnected; reconnect/sync error; action error. | Keep status/account/last sync, retained-snapshot caveat, setup/sign-in/retry/reconnect instructions and errors unchanged. First connection retains Connect your account, then choose which calendars to import. | All branches reviewed; offline and initial failure tests |
| Android same | Manage calendars selection count / Unsaved; remove-all draft. | Keep count, Unsaved and Removing all calendars will remove future imported plans. Review the changes before applying. | Source-reviewed; selection test |
| Android same | Account options expanded; connected or retained disconnected account. | Keep Disconnecting removes future imported plans. Google events and recorded activity stay unchanged. You’ll review the changes first.; Forget import data also clears cached calendar details and remembered choices. | Source-reviewed immediate consequences |
| Web `GoogleCalendar.tsx` | Unconfigured/loading/account connected or disconnected/sync and action errors/success. | Keep configuration/recovery, account, sync and action status unchanged. | Source-reviewed; live unconfigured and preview regression test |
| Both Google confirmations `GoogleCalendar.tsx`, `ui/calendar/GoogleCalendarPanel.kt` | Selection/disconnect/forget/exclude/metadata preview; before/after plan; remaining conflicts. | Keep atomic-plan explanation, unchanged Google events/recording, removal/forget retention/exclusion consequences, metadata scope, before/after and conflict counts unchanged. | Source-reviewed; web concurrent-change test; Day audit continues #325 |
| Both Google nested details, same sources | Pending/excluded occurrence, overlap editing, classification/notes. | No optional explanation to move: factual previous/current intervals, source, fields, choices and errors stay. Web details opens editable content, not help. | Reviewed; shared Settings and Day consumers |
| Web `features/activity/FocusMode.tsx`; Android `ui/focus/FocusMode.kt` | Keep display awake in Focus description. | Help beside setting: web Browser and battery policy apply; manual locking still works.; Android Battery policy applies; manual locking still works. | Implemented; builds / web Focus tests |
| Web `features/activity/CheckInSettings.tsx` | Toggle description + How check-ins work help: native inactivity only, time away insufficient, suspended-page limits/fresh observation, recording unaffected. | Group in Help Inactivity check-ins. First rules unchanged; final Recording continues regardless of detection or notification settings. Remove standalone help row. | Implemented; long-help and narrow browser check |
| Android `ui/day/CheckInSettings.kt` | Ask whether an activity is still going after a quiet stretch.; existing How check-ins work help. | Move toggle description into existing section help; keep usage/screen evidence, missing events, notification and in-app question rules. | Implemented; shared-help validation from #323 |
| Both threshold, same sources | Enabled/disabled Check in after. | Existing field help How long the device must be idle before asking. unchanged; selected duration stays. | Reviewed #323 integration |
| Web detection `CheckInSettings.tsx` | Lets Timebox notice when this device goes idle.; denied recovery. | Remove redundant first clause; keep denied Change this in the site's browser settings. and capability/permission state. | Implemented; conditional source review |
| Web `features/activity/browserCheckIns.ts`, rendered by CheckInSettings | Healthy Native device detection is connected while this page can run. | Shorten visible state to Device detection connected.; page-running rules in Inactivity check-ins help. | Implemented; no detection logic change |
| Web same sources | Suspended/unavailable/permission failure/dynamic paused reason. | Keep Detection paused; unobserved time is not counted.; Device detection is unavailable in this browser context.; Use browser site settings to allow device detection.; dynamic failure unchanged. | All conditions reviewed |
| Android `checkin/AndroidCheckIns.kt`, rendered by CheckInSettingRows | Healthy Approximate detection paragraph: screen-off only, missing history, battery/return delays. | Visible Detecting screen-off intervals.; complete rules moved into How check-ins work help (lack of touch insufficient, missing history unknown, battery/return delay). | Implemented; no detection logic change |
| Android same sources | Checking, Android 8 unavailable, disabled, denied/revoked usage access, locked after reboot, observation restarted, no Activity Tracking, missing history, failure reason. | Keep all statuses, coverage caveats and recovery unchanged, including Enable usage access to detect screen-off intervals. Recording continues. | All branches reviewed |
| Web notifications `CheckInSettings.tsx` | Check-in/Battle Plan usage; connection when question created; missed notifications not replayed. | Group unchanged in Browser notifications help. Denied recovery and Allowed/Blocked/Not supported/Not allowed stay visible. | Implemented; browser and source review |
| Android Reminders `SettingsScreen.kt` | Allowed: Delivery may be delayed by battery restrictions. | Help beside Reminders heading; unchanged. | Implemented |
| Android same | Denied notifications. | Keep both storage facts and cannot display any reminders until notifications are enabled; Enable/Open notification settings recovery unchanged. | Source-reviewed; known old-copy failing test excluded per baseline |
| Android same | Daily planning/review time; Planned Block reminder lead/off; exact alarms unavailable. | Keep chosen values, Applies to every Planned Block scope, Reminders may arrive a few minutes late / Exact alarms are not allowed for Timebox and Allow exact alarms. Time picker has no optional prose. | All conditions reviewed |
| Android voice entry points `SettingsScreen.kt` | Quick Settings tile and default digital assistant. | Help: Use the Quick Settings tile or default digital assistant to start Quick Tracking. Set up stays visible. | Implemented |
| Android setup `quicktracking/QuickTrackingActivity.kt` | Separate setup Activity intro. | Keep Speak an activity. When you finish speaking, Timebox records it automatically. Correct the result later in Assistant → Review. | Material automatic-recording consequence visible |
| Android same | Assistant assigned/unassigned. | Keep assigned status; unassigned shortened to Timebox is not your default digital assistant. Assistant settings / Choose default assistant remain. | Implemented |
| Android same | Default-assistant gesture instruction; Samsung path/model/One UI caveat; gesture navigation/Bixby/power-button guidance. | Group original details in Help Assistant setup, hidden even on first use. | Implemented; separate Activity review |
| Android same | Tile added/already added/not added; older Android manual tile setup; speechStatus. | Keep result/recovery unchanged, including Quick Settings → Edit instructions. OS dialogs excluded; capture overlay audit #329. | All conditional branches reviewed |
| Web API key `GoogleCalendar.tsx`, ApiKeySetting | Stored for this browser tab’s session. Required when your server uses authenticated access. | Help Timebox API key; unchanged. Save connection / Connection saved stay visible. | Implemented; new draft/no-submit integration test |
| Android Advanced `SettingsScreen.kt` | API server location / API_KEY explanation. | Help Advanced: Set the Timebox server address for this device. An API key is required only when your server uses authenticated access. Fields/values/Save stay. | Implemented |
| Android Appearance / preview entry, same | Charcoal surfaces, brighter lanes.; Inspect surfaces, type, controls, and states. | Remove descriptive prose; labels, switch and Open remain. | Implemented |
| Android `ui/theme/ThemePreviewScreen.kt` | Transient Feedback Floating paper…; Surface ladder ordered description; typography/font explanation; Controls description; descriptive body/empty samples. | Remove four section descriptions; Surface ladder → Surfaces; samples Sample body text. / Sample empty-state guidance. | Implemented; sample consequences/recovery unchanged |
| Android Home `ui/components/NavigationDirectory.kt` | Directory groups, destinations, Current and Edit shortcuts. | No applicable explanatory copy; labels retained. | Reviewed no-copy surface |
| Android Edit shortcuts `ui/components/NavigationShortcuts.kt` | Keep your most-used screens within reach. Changes apply only to this device. | Remove introduction, shorten scope to This device. | Implemented; navigation tests |
| Android same | Empty bar, selected count, Save/Cancel, save failure, accessible move/remove. | Keep No shortcuts selected, count and Couldn't save shortcuts. Try again. No instructional hover hints present. | All branches reviewed; navigation tests |
| Web `components/Layout.tsx` | Sidebar/mobile navigation labels, branding, route state, Settings. | No applicable instruction/explanation; tagline is branding. | Reviewed no-copy surface |
| Web `components/ThemeToggle.tsx` | title Light mode / Dark mode; accessible Switch to… mode. | Keep ordinary icon labels; not instructional help. | Reviewed no-copy surface |

### Shared consumers and absent counterparts

- Web FocusWakeSettings, CheckInSettings, BrowserNotificationSettings, ReportingTimezoneSettings and ApiKeySetting each have only SettingsPage as a production consumer. Android FocusWakeSettingRow appears in Settings and Activity Tracking's expanded controls: both inherit the help. CheckInSettingRows is Settings-only.
- Web GoogleCalendar appears in Settings (no date) and TodayPage (date); setup/priority changes apply to Settings. Android GoogleCalendarPanel appears in Settings and DayScreen; GoogleCalendarSettings is only its no-date branch. CalendarSelectionList has only GoogleCalendarSettings as a consumer. Shared nested editors/confirmations retain visible consequences on both routes; #325 finishes Day audit.
- Web SettingsDetails remains the shared contextual-help wrapper introduced in #323, with no production consumers after grouping check-in help. No inline explanation-only expander remains in this family. Google Calendar expanders reveal controls or results.
- Quick Tracking setup is a separate Activity using the same ContextualHelp without the main navigation host. Home/shortcut editor, Quick Tracking setup/device instructions, reachable Theme preview, server-address field, Daily Reminder and Planned Block reminder settings have no web Settings counterpart. Browser session-storage and idle-permission details remain platform-specific.
- SettingsViewModel has result handling but no additional optional explanation surface. Navigation destination declarations and theme token/type files have no applicable production explanatory copy. Focus overlay and Activity Tracking content beyond the shared wake setting remain for #325; Quick Tracking capture remains for #329.

### #324 validation and review (2026-10-26)

- Web production build passed after final copy changes. Focused Vitest: 19/19 across ContextualHelp, ReportingTimezoneSettings, FocusMode and GoogleCalendar. New API-key integration check verifies help does not submit/save or discard the draft. Focused ESLint: zero errors; two existing GoogleCalendar ref-cleanup warnings remain. Browser detection copy-only change also passed lint.
- Android assembleDebug and instrumentation compilation passed. Focused device suite: 17/17 across GoogleCalendarSettingsTest, ReportingTimezoneSettingsTest and NavigationShortcutsTest. Calendar selection test now opens/closes priority help with an unsaved selection. Final healthy-status/help wording was rebuilt and reinstalled after this run; no logic changed. Full instrumentation suite was not run; baseline failures remain outside this focused result.
- Chromium review: light desktop short popover; keyboard activation, Escape, focus return and UTC time-zone draft preservation; long check-in dialog; dark 360px viewport at 200% text. Measured dialog scrollWidth and clientWidth both 328px, with scrollable long text and no horizontal help overflow. Restored normal text, light desktop, original settings values with an explicit reload. Browser accessibility snapshot verified names and source reading order; no audio screen-reader run.
- Android review: installed final APK with `-PreviewApiBaseUrl=http://10.0.2.2:12106/`; separate Quick Tracking setup showed automatic recording consequence and assistant status before help. Long Assistant setup sheet opened at 200% text, retained all platform caveats and exposed Close help after scroll. Touch, explicit close and Back checked; normal font restored. Final Settings short Reporting Time Zone popover checked and dismissed. Existing #323 dark-theme/helper and nested-editor validation reused; no new TalkBack audio, physical-device/Samsung setup, live OAuth or exhaustive OS-permission exercise.
- Updated review remains running: web http://127.0.0.1:12107/settings, API 12106, isolated SQLite C:/Users/Caius/TimeboxRuntime/contextual-help-322.db. Android emulator-5606, owner initiative-322 contextual help review, token f1c680cd89aa47e591ea6c965c823af6, root C:/Users/Caius/TimeboxRuntime/emulators, returned to review hold. Settings open with help dismissed, normal text, light theme. Unrelated reviews untouched.
- Evidence outside Git: C:/Users/Caius/TimeboxRuntime/help324-web-light.png, help324-web-long.png, help324-web-large.png, help324-android-setup.png (closed setup page), help324-android-large.png and help324-android-settings.png; UI XML snapshots alongside them. One immediate post-launch UI dump returned no root; a fresh dump after launch settled succeeded. Inventory was normalized from mixed legacy encoding to UTF-8 while retaining prior evidence.

## Day, activity recording and Focus #325

Paths below are relative to `frontend/src/` or Android `ui/`. Conditions are source-reviewed; unchanged means the original wording remains final. Shared help interaction validation from #323–324 applies; batch-specific checks follow.

| Platform / source / surface | Condition and original wording/group | Decision; final wording / destination | Status |
| --- | --- | --- | --- |
| Web `today/ReadyToPlanDrawer.tsx` (under features) | Ordinary mobile/desktop queue: Drag a task to Planned, or select it and choose a time slot.; per-task title Drag onto the Planned timeline | Move unchanged instructions to Ready to Plan help by heading; remove redundant title. Search/no match/no waiting tasks and selection remain. | Implemented; browser and drawer tests |
| Android `day/PlanMode.kt`, `DayScreen.kt` | Empty queue, selected accessibility task, loading/failure | Keep Nothing waiting to be planned.; Mark a Battle Plan Task Ready to Plan when you want to schedule it.; Tap an open time beside selected task; Tasks unavailable and retry. These describe empty states or the active selection step. No permanent gesture tutorial to migrate. | Source-reviewed |
| Both Day view options `today/DayViewOptions.tsx`, `day/DayViewOptionsDialog.kt` | Always Choose what stays in view. | Remove redundant introduction. Keep Activity Tracking keeps running when hidden. visible. Zoom/current value/reset are controls, no optional prose. | Implemented; web tests |
| Web `components/TimeBlockInspectorContent.tsx` | New Planned Block introduction | Shorten both creation branches to Choosing a Task Type creates the block automatically. Keep visible because selection saves immediately. | Implemented; inspector/Day regressions |
| Both Planned editors same web source / `day/BlockSheet.kt` | Existing ordinary block drag/resize | Existing Adjust block time help from #323 retained; web edge adjustment and Android move/groove instructions retain platform differences. | Reviewed; prior nested time draft check reused |
| Both same Planned editors | Google-linked/frozen; ordinary existing edits | Keep Google name/time ownership and locally saved classification/notes; web Edits save as you make them. is immediate autosave behavior. Keep source link and frozen deletion versus leave-out confirmation. | Source-reviewed |
| Android `day/BlockSheet.kt` | Already-recorded related card: Plan notes stay on the plan. | Move unchanged to Plan notes help beside Open actual. | Implemented |
| Both Planned editors | Already recorded, partial recording/override, future unavailable; recording action and deletion | Keep recorded count/duration, linked Actual actions, differing-time ranges, Available after this block starts., replacement action and plan-only deletion consequences. No behavioral change. | Source-reviewed; web recording tests |
| Web `activity/ActivityActualEditor.tsx`, Android `day/ActivityActualEditor.kt` | Task-linked Actual: Task Completion stays independent. / Recording time does not change Task Completion.; Android Completed prefix | Consolidate as Recording time help: Recording time does not change Task Completion. Available for ordinary/new Actual too; Task identity/status stays visible. | Implemented; new draft-preservation web test |
| Android `day/LogTimeForm.kt` | New Actual: Records past time. Doesn't complete a task. | Recording time help: Records past time. Recording time does not change Task Completion. | Implemented; draft/help/save device test |
| Both Actual editors, above | Running/correction/invalid range/deletion | Keep Use Switch or Stop above the Day timeline to correct the Current Activity.; errors; reporting zone; delete leaves time unrecorded (Android adjacent activities unchanged). | Source-reviewed; correction tests |
| Android `day/RunningActualSheet.kt` | Running editor | Keep End · recording continues, Now and Reporting Time Zone; status/actions and user note/task description are factual content. No optional prose. | Reviewed no-copy migration |
| Web `components/BlockTimeFields.tsx`, `activity/ActivityTimeField.tsx`; Android `day/ActivityTimeField.kt`, BlockSheet time fields | Invalid/ambiguous/nonexistent local time, reporting zone/date context | Keep HH:mm validation, occurrence selection, nonexistent-time warning and chosen offset/zone. Field labels/values and ordinary pickers have no optional explanation. | Source-reviewed; tests |
| Both `activity/ReportingDayActuals.tsx`, `day/ReportingDayActuals.kt` | Clock-change day | Keep clock-change explanation, elapsed daily shares and zone. | Source-reviewed |
| Android `day/StartTrackingSheet.kt`, `SwitchActivitySheet.kt` | Expanded start/change timeline: Drag the line. Hold near an edge to keep scrolling. | Same wording in Start time / Change time help beside timeline. | Implemented |
| Android `day/StopTrackingSheet.kt` | Stop timeline: Drag the line or tap a time. Nearby block boundaries snap into place. | Same wording in Stop time help inside When did you stop? section. | Implemented |
| Web `activity/SwitchActivityTimeline.tsx` | Expanded change timeline gesture hint | Change time help: Drag the line. Hold near an edge to keep scrolling. Use the arrow keys to adjust the focused line. Outside pointer-sensitive timeline. | Implemented |
| Web `activity/ActivityTracking.tsx` | 15 min ago unavailable: duplicate title and visible shortcutReason | Remove instructional title; reason stays visible below shortcuts. | Implemented |
| Both tracking start/switch/stop and Plan Now `ActivityTracking.tsx`, `PlanNowEditor.tsx`, SwitchActivityTimeline / Android corresponding sheets and timeline | Ordinary/running/backdated/unavailable/replacement/gaps/current changed | Keep starts at confirmation/now, previous end/new start, recording continuity, Running Time does not restart, exact replacement/resumption ranges, filled/unrecorded gap, Undo, busy/error/invalid time and connect/sync recovery. Keep Choose 1–90 minutes as current input constraint. | Source-reviewed; web recording/Plan Now regressions |
| Both Record Actual previews `today/RecordingPreview.tsx`, `day/RecordingPreview.kt` | No overlap/overlap/stale/running/note changes | Keep actual replacement range, before/after, retained time, running continuation, stale preview re-review and name/note replacement disclosure. Keep The plan is kept. Task completion is unchanged. as confirmation consequence; optional general rule is in editor help. Detail expander contains actual changed records, not general instruction. | Source-reviewed; web preview tests |
| Android `day/CurrentActivityNotesSheet.kt` | Linked/standalone/read-only/offline/loading/error | Keep whole-description/whole-note replacement, linked Task Trash/archive read-only reason, Connect to save notes and load failure recovery. User-authored content unchanged. | Source-reviewed |
| Both Google Day panel `calendar/GoogleCalendar.tsx`, `calendar/GoogleCalendarPanel.kt` | Pending/excluded/updated/frozen/conflict edit/metadata/confirmation | All actual occurrence details/previous times/source, editable field restrictions, before/after changes, pending count and atomic confirmation consequences remain. Shared #324 inventory applies fully to Day consumers. Calendar updates and metadata expanders reveal factual records/controls, not optional help. | Source-reviewed; prior calendar tests reused |
| Both Focus and inactivity `activity/FocusMode.tsx`, ActivityTracking / `focus/FocusMode.kt`, `day/ActivityTracking.kt` | Focus, planning conflict, pending question, rejected changes/device observations, permissions | Keep recording continues while deciding, finish/cancel planning to enter Focus, usage-access recovery and recording independence, wake/error status, rejected changes not replayed and Day correction recovery, dismissal-retains-data consequence. Existing data expanders show recovery payloads. Shared wake help from #324 retained. | Source-reviewed; previous Focus tests reused |
| Both Day main/timeline/header/calendar `TodayPage.tsx`, components DayTimeline/TimeBlockCard/DayCalendarPopover / `day/DayScreen.kt`, DayTimeline, DayCalendarHeader, CurrentActivityControl, PlannedBlockSuggestion, UntrackedGaps, DayLoadProblem | Loading/error/offline/cached/history/today/selected draft/no space/future Actual | Keep failure/retry/cached/offline sync caveats, future Actual prohibition, no room, chosen task/place-or-cancel guidance and date/zone values. Ordinary icon/date titles unchanged. Timeline geometry/handles contain no tutorial copy to migrate. | Source-reviewed |

### Shared consumers and absent counterparts

- Web ReadyToPlanDrawer is used in desktop rail and mobile Day; Actual editor is shared by new/correction and inspector rail/sheet. TimeBlockInspectorContent also routes actual edits there. Android BlockSheet routes Actual editing into ActivityActualEditor, whose new-entry branch uses LogTimeForm. Recording time rules are thus present in both editor paths without duplicating permanent prose.
- Android SwitchActivitySheet handles start/switch/edit/Plan Now paths, with expanded correction help; StartTrackingSheet also supplies standalone start. Web SwitchActivityTimeline is used by both ActivityTracking and PlanNowEditor. Help is outside gesture hit regions and does not change timeline callbacks.
- Focus reuses ActivityTracking and its confirmations/status on web; Android Focus hosts tracking and linked-task controls. BrowserCheckInHost and ActivitySwitchUndoHost are routing/lifecycle/status hosts, with no additional optional copy. ActivityTrackingStatus and Android status helpers retain all offline/sync/retry state labels.
- Android CurrentActivityNotesSheet/running sheet and drag-return planning queue have no equivalent standalone web surface. Web retains existing field editing and its platform-specific tracking flow. No parity features added. Quick Tracking capture and Assistant correction remain #329 scope; shared Task Type picker content remains #328 scope.
- Development-only PlanNowPrototype and RecordingPreviewStudy are excluded. Day drawing/model/geometry helpers, navigation callbacks and calendar controls have no further optional explanatory copy. User-authored notes/descriptions/task names remain untouched.

### #325 validation and review (2026-10-26)

- Final web production build and focused ESLint passed with zero errors/warnings. Focused Vitest across Day, recording preview, view options, drawer, Actual editor, activity tracking/correction and Plan Now: 93/96 passed. The three TodayPage failures are duplicate `role=alert` queries (the expected placement error plus Google Calendar's unmocked `not found`). All three reproduced with every edited frontend source restored temporarily to accepted commit `2d7e959`; implementation files were then restored. The additional Actual editor help/draft/no-save regression passed in its 6/6 file run. Full web suite not run.
- Android final assembleDebug and instrumentation compilation passed. Focused device run: 2/6 passed, including both LogTimeForm cases and the added typed-name → help → close → save check. Four failures (StopTrackingTimeline missing Read ends; ActivitySwitchTimeline missing Evening meal and Dinner ends; DayViewPreferences persisted tracking flag) all reproduced unchanged against accepted commit `2d7e959` with edited Android sources temporarily restored, then restored to the final implementation. These extend evidence for this batch; they are not claimed as master baseline or fixed here. Full instrumentation suite not run. Final APK rebuilt after restoring changes and installed with `-PreviewApiBaseUrl=http://10.0.2.2:12106/`.
- Chromium: Ready to Plan help opens with click and keyboard Enter; Escape returns focus to trigger. Native accessibility snapshot exposes named help, heading, content and close control. Desktop/light and 360px/dark/200% text checked; dialog width and scrollWidth both 328px, readable without horizontal overflow. Normal/light desktop restored. Actual editor draft preservation is automated; prior nested Planned-time draft and shared helper tests reused.
- Android: touch opened Start time anchored help inside expanded Start tracking; screenshot verified instruction and explicit Close help. Back dismissed it. The LogTimeForm device test verifies explicit close retains typed draft and does not save. Prior #323–324 large-text/dark helper/nested editor checks reused; no additional audio TalkBack/screen-reader session, physical Samsung, live OAuth, exhaustive clock-change or offline device exercise. A dump immediately after app launch returned no root; retry after settling succeeded. A temporary 200% font setting recreated the Activity; normal font restored without recording anything.
- Updated Day remains available at http://127.0.0.1:12107/day/2026-10-26, API 12106 with isolated SQLite `C:/Users/Caius/TimeboxRuntime/contextual-help-322.db`. Vite reload reflects final source. Android emulator-5606, token `f1c680cd89aa47e591ea6c965c823af6`, owner `initiative-322 contextual help review`, storage `C:/Users/Caius/TimeboxRuntime/emulators`, final build installed and Day open at normal font/light theme, no tracking started, help dismissed, review hold restored. Unrelated reviews preserved.
- Evidence outside Git: `help325-web-day.png`, `help325-web-large.png`, `help325-android-short.png`, `help325.xml`, `help325-baseline-web.log`, `help325-baseline-android.log` under `C:/Users/Caius/TimeboxRuntime/`. Baseline log capture initially hit Windows console encoding errors; the retained UTF-8 logs and final assertions establish the results above.

## Tasks, Projects and recurrence #326

Paths below are relative to `frontend/src/features/battle-plan/` (web) and `android/app/src/main/java/com/timebox/android/ui/battleplan/` (Android). All groups are source-reviewed; changed interaction checks and limits follow below.

| Platform / source | Condition and original group | Treatment, final wording and placement | Status |
| --- | --- | --- | --- |
| Web `TaskDetailPanel.tsx` | Incomplete Task: Planning readiness is separate from work status. | Help: Ready to Plan; wording retained beside readiness control. Pending save and ReadinessFailureNotice stay visible. | Implemented |
| Web `BattlePlanCard.tsx`, `BattlePlanPage.tsx` | Incomplete card title: Drag task to change its status | Help: Task status beside Battle Plan heading, “Drag a task to change its status.” Remove instructional title on every card; retain Move task accessible name, Completed Task title, readiness action names and priority labels. | Implemented |
| Web `BattlePlanSidebar.tsx` | Every Project handle title: Drag to reorder, or press Space and use arrow keys | Section help: Project order; “Drag a handle, or focus it and press Space then use arrow keys. The project menu also has Move up and Move down.” Saving order, moved-position announcement and failure/retry remain visible. | Implemented; sidebar tests |
| Android `ProjectOrderDialog.kt` | Reorder projects instructions: Hold a drag handle to move a project, or use the arrows. | Same wording in Project order help beside dialog title. Arrow actions, saving state and error remain. | Implemented |
| Web `RecurringPage.tsx` list | Always: Templates create independent Battle Plan tasks seven days ahead. | Recurring tasks help: “Recurring Task Series create independent Battle Plan Tasks seven days ahead.” | Implemented |
| Web `RecurringPage.tsx` form | Monthly schedule: Short months use their final day. | Same wording in Day of month help immediately after field, outside its label. | Implemented |
| Android `RoutineRepeatEditor.kt` | Quota mode session timing; monthly schedule short-month rule; Ends/After cycles definition | Field help: Quota sessions “Choose when to do each session within the [day/week/month].”; Day of month “Short months use their final day.”; Cycles “A cycle is one recurrence period, not one completed task.” | Implemented |
| Both `RecurringPage.tsx` / `RoutineRepeatEditor.kt` | Scheduled-only keep-overdue explanation | Unfinished occurrences help: “After its recurrence period, unfinished work leaves current work unless kept overdue or linked to a future Planned Block.” Includes the future-block exception from ADR 0001. Checkbox and its label remain visible, no change to recurrence behavior. | Implemented |
| Web `RecurringPage.tsx` form | Pre-planning none/queue/time descriptions | Contextual Pre-planning help beside select; retains all three conditional wordings: create occurrences without queue/time; add each occurrence on its date without time; create attached Planned Blocks in the seven-day horizon. Actual invalid slot errors remain visible. | Implemented; nested draft test and browser |
| Web `RecurringPage.tsx` detail | Ready to Plan · Each occurrence enters the queue on its date. | Keep current value Ready to Plan visible; move rule into Pre-planning help: “Each occurrence enters the queue on its date, without assigning a time.” | Implemented |
| Android `RoutinePreplanningFields.kt` | Three destination descriptions and “Schedules when this time is free.” | Grouped Pre-planning help: “No pre-planning leaves each occurrence for you to plan. Ready to Plan adds it to the queue on its date without assigning a time. Planned time reserves a slot when it is free, within the seven-day horizon.” Mode labels remain selectable, detailed instructions hidden. | Implemented; nested draft device test |
| Android `RoutineScreen.kt` | Checklist: Included in each session/occurrence. | Checklist inheritance help: “Each [Session Task/Task Occurrence] receives these Checklist Items as Subtasks.” Both mode variants retained. | Implemented |
| Android `TaskSubtasks.kt` | Add form count plus Add another or close to return | Remove redundant action instructions; keep “[count] subtasks.” Add and close controls remain. | Implemented |
| Both recurrence mode controls | Android CreationMode schedule/quota distinctions; web On a schedule / Times per period; existing mode fixed | Keep concise choice distinctions and Android “Chosen when this routine was created.” unavailable-change reason. Web mode remains disabled for existing series. | Preserved |
| Both `RoutineScreen.kt`, `RoutineRepeatEditor.kt`, `RecurringScreen.kt` / `RecurringPage.tsx` | Create/edit preview, past cycles, upcoming dates, lifecycle and validation | Keep concrete backfill task/cycle counts, explicit backfill confirmation, end removal warning and Android customized/completed preservation, delete detaches generated tasks and cannot recover template, incompatible-slot review guidance, actual unavailable configured slots/dates, load/save/retry/discard errors. Draft values, cadence and start/end summaries remain factual visible state. | Source-reviewed; routine and web tests |
| Both `BattlePlanScreen.kt`, `TaskFieldsSheet.kt`, `TaskSubtasks.kt`, `MoveProjectMenuItem.kt` / `BattlePlanPage.tsx`, `ProjectEditor.tsx`, `TaskDetailPanel.tsx`, `MoveProjectDialog.tsx`, `SubtaskTitle.tsx` | Project delete, Trash, permanent delete, restore, discard, partial task creation, recovered edits, frozen Task, completion, reminder | Keep counts/irreversibility, 30-day restoration, Subtask effects, discarded-field versus saved-work consequences, conflict choices, validation, frozen-task reopen reason, completion records, reminder disabled-device warning and clear/reminder controls. Existing reminders/errors/readiness recovery stay visible. Quota progress is factual data; derived Session Task accessible explanation retained. | Source-reviewed; task/Project regressions |
| Both filters/list/board `BattlePlanScreen.kt`, `TaskTypeFilterSection.kt`, `ProjectNavigationList.kt` / `BattlePlanPage.tsx`, `TaskTypeFilterMenu.tsx`, `BattlePlanCard.tsx` | Empty collections/status/type search, selected filters, counts, sort/group controls | Keep useful Nothing here/No tasks/No type matches empty guidance and selection/status/counts. No further optional explanatory copy. Ordinary names and data previews retained. | Reviewed |
| Both task composer/field editors `TaskComposerSheet.kt`, `TaskFieldsSheet.kt`, `TaskSubtasks.kt` / `TaskComposer.tsx`, `TaskDetailPanel.tsx`, `SubtaskTitle.tsx` | Title/description, Project selection, priority, Task Type, date/time, Subtask add/rename | No other optional copy: placeholders, constraints, reporting zone and labels retained; user descriptions untouched. Task Type picker shared content owned by #328. | Reviewed |
| Android `RoutineCalendarSection.kt` and both recurrence list/details | Calendar/upcoming/completed/current work, active/paused/ended, missing/error/empty | Keep actual task links, date counts, No current tasks/No completed tasks yet and retry; recurrence list empty state still offers schedule/quota creation. No tutorial copy in calendar controls. | Reviewed |
| Android `RoutineScreen.kt` | Track as habit / Show in Chronicle › Habits; Checklist Habit count/switches | Keep destination cue as a current choice distinction; Habit behavior and drilldown belong to #327. | Reviewed |

### Shared consumers and absent counterparts

- Web BattlePlanSidebar appears on Battle Plan, Recurring and Task Types, desktop and mobile; all share the migrated Project order help. Web TaskDetailPanel also opens linked Tasks from Day and other routes; readiness help does not alter saving. Android ProjectOrderDialog serves Project navigation; TaskFieldsSheet and TaskSubtasks serve creation/details and routine subforms as applicable.
- Android RoutinePreplanningFields is shared by the dedicated Pre-planning editor and Repeat when configured slots must be reviewed after schedule edits. Both use the same section help. Android RoutineRepeatEditor serves new/existing schedule and quota modes. Web TemplateForm serves create/edit and retains the same conditional help for both.
- Android `RecurringCreationScreen.kt`'s old `RecurringPreplanningScheduleEditor` is only called by debug `RecurringDetailsHierarchyPrototype`, so its inline explanation is excluded. Production uses RoutineScreen and RoutinePreplanningFields. Prototype-only routines remain excluded.
- Web has no Android routine Calendar or Checklist Habit switches; web quota checklist-unavailable explanation remains visible as an existing platform restriction. Android has no web whole-form recurrence editor or web keyboard drag instruction. No parity additions.
- All production files in the two feature directories were scanned. ViewModels/state/model/geometry helpers have no additional optional rendered prose; validation and failure strings are retained. No backend/domain behavior changed.

### #326 validation and review (2026-10-26)

- Final web production build and focused ESLint passed. Focused web tests total 71 passing cases across RecurringPage, BattlePlanSidebar, BattlePlanPage, ProjectEditor and SubtaskTitle. Initial BattlePlanPage run was 47/48: its old assertion expected the intentionally removed instructional title; updated to assert the existing accessible Move label, then all 48 passed. New recurrence test verifies help initially hidden, typed title and selected pre-planning mode retained, and no confirm/create invoked. A test-only unsupported exact query option caught by TypeScript was removed; final build passed. Full web suite not run.
- Android RoutineScreenTest passed 7/7 on the owned emulator, including new nested slot-draft/help/close preservation without saving, existing slot removal/save, unavailable slot, Subtask/current work, repeat and explicit end confirmation checks. Debug and instrumentation APK compilation passed. No baseline failures in this selected suite; broader known baseline not rerun. No full unit/instrumentation/backend suites.
- Chromium normal/light desktop and 360px dark/200% text: Pre-planning help opened with keyboard Enter, Escape kept parent recurrence form and returned focus, typed title retained; screenshots inspected for readable text and close control. Accessibility snapshot exposes named trigger, heading, dialog and content. Android touch opened longer help inside Pre-planning; screenshot inspected, Back dismissed only help and retained editor; explicit close covered by device test. Prior shared helper large-text/dark and one-panel checks from #323–325 reused. No new audio screen-reader, physical device or live OAuth checks.
- Both old review servers had stopped. Restored API 12106 from this tree using `C:/Users/Caius/Desktop/timebox/backend/.venv/Scripts/python.exe`, isolated SQLite `C:/Users/Caius/TimeboxRuntime/contextual-help-322.db`, Asia/Singapore and activity-tracking development opt-in; restored Vite 12107 proxying to it. Standard launcher uses separate PostgreSQL/default ports, so was not used to replace this isolated review. System Python lacked langchain_openrouter. Automatic review rejected background Start-Process and combined navigation/device/lint and documentation/commit commands as “blocked by policy” without a specific reason. Separate permitted foreground server sessions and individual checks succeeded.
- Final Android APK reinstalled after instrumentation and launched with `-PreviewApiBaseUrl=http://10.0.2.2:12106/`. Recurring Tasks list remains open, normal font/light theme, no routine created or tracking started. Emulator `emulator-5606`, token `f1c680cd89aa47e591ea6c965c823af6`, owner `initiative-322 contextual help review`, storage `C:/Users/Caius/TimeboxRuntime/emulators`, review hold restored. Other reviews preserved. Web Recurring runs at http://127.0.0.1:12107/battle-plan?view=recurring with help dismissed.
- Evidence outside Git: `help326-web-light.png`, `help326-web-large.png`, `help326-android.png`, `help326.xml` in `C:/Users/Caius/TimeboxRuntime/`. Shared helper behavior reused; no claim of exhaustive interaction with every conditional variant.

## Chronicle, Habits and Time Goals #327

Paths below are relative to `frontend/src/features/history/` and Android `ui/chronicle/`. Every production source in these directories was scanned; reporting models and mutation logic are unchanged. “Retained” means original wording is also final wording.

| Platform / source / surface | Condition and original group | Treatment; final wording and placement | Status |
| --- | --- | --- | --- |
| Web `HistoryPage.tsx` | Calendar introduction: Browse planned, recorded, and completed days through today. Any date opens in Day. | Move unchanged to Calendar help beside heading. | Implemented; month-preservation test and browser |
| Web `HistoryPage.tsx` | Trends introduction: Recorded time by Task Type. | Remove redundant introduction; table retains Task Type heading, recorded total and reporting zone. | Implemented; reporting tests |
| Android `ChronicleScreen.kt` / ChronicleMonthPage | Always: A dot means time was recorded. Tap any date to open Day. | Move unchanged to Calendar help beside grid. Dot/date accessible descriptions retained. | Implemented; calendar device tests |
| Both `HistoryPage.tsx`, `ChronicleMonthGrid.tsx`, `ChronicleHighlight.tsx` / `ChronicleScreen.kt` | Empty month, loading/error/retry, month navigation, Trends drill highlights | Keep web No planned, recorded, or completed days this month.; loading/error; Android error/retry. Date range, source, contribution summary and recorded-day labels remain factual visible/accessible data. No other optional copy. | Source-reviewed; reporting tests |
| Both `TrendsPanel.tsx`, `trends.ts` / `TrendsScreen.kt` | Period selectors, results, direct child rows, empty/refresh/failure/invalid range | Keep Recorded time · zone, selected dates and inclusive day counts, shares/totals, No recorded time in this range., Updating recorded time, errors and Retry. Directly under labels and Show contributing days names identify real data/actions. No optional rules to migrate. | Reviewed no-copy migration; web reporting tests |
| Web `CustomRangePicker.tsx` | Future disabled days: browser title Days after Today have no recorded time | Remove repeated title; keep same sentence visible below calendar because it explains unavailable dates. First/Last day, Tap a day and Choose the last day remain current selection guidance. | Implemented; existing custom-date draft/apply tests |
| Android `CustomRangeSheet.kt` | Date selection and disabled future dates | No explanatory title/inline rules existed. Keep First/Last day, Tap a day, Choose the last day, date values and Show count. No new parity behavior or help. | Reviewed no-copy migration |
| Android `HabitsScreen.kt` | Nonempty week: Tap a day to record it. Long-press a quota day to remove a session. | Move unchanged into Record habits help beside week grid. | Implemented; shared interaction reused |
| Android `HabitsScreen.kt` / Add habit sheet | Choose a routine, or one of its checklist items. Past completions and checks count. | Choose a habit help beside heading: Choose a routine or checklist item. Past completions and checks count. | Implemented |
| Android `HabitsScreen.kt` | Empty collection/week/candidates; failures, offline; quota session sheet | Keep No habits yet; Choose routines to track here. Their past completions are included.; No habits were active this week.; Every active routine and checklist item is already a habit.; Offline · showing the last loaded week.; failure/Retry/Dismiss. Session count, date, Add/Remove one session labels, scheduled/checklist outcomes and cadence totals stay visible. | Source-reviewed |
| Android `TimeGoalsContent.kt` | Always: Recorded time, measured against each goal’s period; expanded row Select a day to view its period | Consolidate in Time Goals help: Recorded time is measured against each goal’s period. Expand a goal and select a day to view its period. | Implemented |
| Android `TimeGoalsContent.kt`, `TimeGoalsScreen.kt` | Empty/current/ended/pending target/loading/offline/error | Keep No time goals for this week; Choose a Task Type and a repeating time target. Your Actual Blocks supply the progress.; all dates, cadence, progress/outcomes, Ended and next-target effective dates. Keep Offline · last updated … / Unsynced tracked time is not included.; Retry/errors. | Source-reviewed |
| Android `TimeGoalEditor.kt` / new and existing | First period inline expander with general counting/carry-forward rules; existing Includes time in all descendant Task Types. | Replace optional prose with Goal progress help by editor heading: Actual Blocks in this Task Type and its descendants count, including existing recorded time. Extra time does not carry forward. Remove expander; actual first-period date range and reporting zone are always visible. | Implemented; creation device test |
| Android `TimeGoalEditor.kt` / nested type selection | Time in this Task Type and its descendants counts. | Same wording in Task Type time help in nested type editor; existing-edit picker uses parent Goal progress help. | Implemented |
| Android `TimeGoalEditor.kt` / nested period and existing Repeat section | Weeks start Monday. Months follow the calendar. | Same wording in Goal periods help; now discoverable in both new nested period and existing Repeat field section. | Implemented; nested draft/close/save device check |
| Android `TimeGoalEditor.kt` | Shortened first period, invalid duration/date, replacement versus target-only edit, offline/error/discard | Retain This first period is shorter. Its full target still applies.; duration bounds and Choose an earlier date; replacement today/excused unmet final period/full replacement target/history retained; next-period effective date/current target and earlier targets unchanged; reporting zone; Connect to save changes. Your draft is kept here./Retry connection; unsaved changes lost confirmation. | Source-reviewed; creation/discard checks |
| Android `TimeGoalDetails.kt` | Includes this Task Type and its descendants. | Move unchanged to Goal progress help. Reporting time zone stays visible. | Implemented |
| Android `TimeGoalDetails.kt` | Credited blocks, empty period, upcoming/ended/next-target/deletion/offline | Keep Durations show time credited within this period. because shown block boundaries can span periods; dates, Running, credited durations, outcome, history retained, not-started reason, pending target date, Connect to manage this goal. and all errors. Delete confirmation retains permanent targets/results removal and Actual Blocks stay unchanged. | Source-reviewed; deletion device test |

### Shared consumers and absent counterparts

- Calendar help belongs to the shared Android month grid; adjacent swipe-preview month compositions use the same source but expose the help control only on the interactive current page. Android Chronicle route delegates Trends, Habits and Time Goals to their respective existing tabs. Web History owns Calendar/Trends with a retained hidden Trends panel; no Habits or Time Goals counterpart exists and none was added.
- Android Goal progress is available in both new/edit TimeGoalEditor paths, including its existing-edit type picker; the separate new-entry type sheet owns its field help. Goal periods is shared by the new nested period sheet and existing Repeat section. Details reuse the same descendants rule but retain the period-credit interpretation inline. Row expansion exposes actual daily data and remains an ordinary data expander.
- Habit headings, checklist rows, total cells and quota session adjustment sheet contain data/control names, not further tutorial copy. HabitEdits and all three reporting ViewModels contain no additional optional rendered explanations; error and validation messages remain. Calendar source summaries, highlight ranges and generated labels remain unchanged.
- TaskTypePicker and WheelDatePicker are shared consumers; their broader content is owned by #328, while #327 covers their surrounding goal-specific explanations. Goal lifecycle has edit/replace/delete; removed archive/history screens and debug goal prototypes are not production scope.

### #327 validation and review (2026-10-26)

- Web production build, focused ESLint and all 17 reporting tests passed (HistoryPage, TrendsPanel, ChronicleHighlight). Added help-open/close test preserves an older selected month. First test invocation mistakenly ran from repository root without the frontend DOM setup; rerunning in frontend resolved those environment failures. The new test initially used the wrong close accessible name, corrected to Close help: Calendar. No product regression remains in selected tests.
- Android debug/instrumentation APK builds and 11 selected device cases passed across TimeGoalCreationTest, TimeGoalDeletionTest and ChronicleScreenTest. Extended creation test opens/closes Goal periods inside the nested period editor and verifies no save/dismiss occurs, then saves the selected month and retained duration correctly. Existing discard, deletion and calendar/swipe checks passed. A final interactive-page guard excludes offscreen preview help buttons; final APK rebuilt/reinstalled after that guard, which was not followed by another instrumentation run. Initial command needed its API URL argument quoted; a removed Icons import still used by the sentence control was restored before the successful builds.
- Chromium: click and keyboard Enter open Calendar help; Escape returns focus to its named trigger. Accessibility snapshot exposes heading, content and explicit close name in order. Light desktop and 360px dark at 200% text screenshots inspected; help width/scrollWidth both 328px, readable without horizontal overflow. Normal light desktop restored. Existing custom range tests preserve select/apply behavior; future-date restriction now visibly explains disabled days.
- Android final app: touch opened Goal progress within New Time Goal; screenshot inspected for counting/carry-forward copy and explicit close. Back dismissed only help and retained editor; cancel returned to Time Goals without creating data. Automated nested-period test covers explicit Close help and draft preservation. Prior #323–326 large-text/dark/one-panel behavior reused; no audio screen-reader/TalkBack, physical-device, full suites, exhaustive offline/clock-change or live OAuth checks. Known instrumentation baseline was consulted; none of the selected cases failed.
- Existing Vite 12107 and API 12106 remained running, using isolated SQLite C:/Users/Caius/TimeboxRuntime/contextual-help-322.db. Web review: http://127.0.0.1:12107/history. Android built with `-PreviewApiBaseUrl=http://10.0.2.2:12106/`, final APK installed and launched; Time Goals open at normal font/light theme, help dismissed. Emulator emulator-5606, owner initiative-322 contextual help review, token f1c680cd89aa47e591ea6c965c823af6, storage C:/Users/Caius/TimeboxRuntime/emulators, review hold restored. Other reviews untouched.
- Evidence outside Git: help327-web-light.png, help327-web-large.png, help327-android.png, help327.xml and help327-back.xml under C:/Users/Caius/TimeboxRuntime/. An immediate post-launch XML dump had no root; a settled retry succeeded. No domain/backend behavior or tracker state changed.
