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
