# Android instrumentation baseline

First recorded execution of `android/app/src/androidTest`: 208 cases across 39 files,
on the managed pool's `timebox-agent-04` (stock Android 36 Google Play x86_64,
1080x2424 at density 420). Before this, the suite had only ever been compiled.

19 cases fail. None is a regression: every one of the 14 reproducible failures was
run against `master` at `53c4d99` and fails there identically. The other 5 are flaky.

Re-establish the baseline with `scripts/android-emulator.py`, per
`docs/agents/android-emulators.md`. Do not run the backend or frontend suites at the
same time; see "Load" below.

## Already broken (14)

Confirmed failing on `master`. Each asserts something the application does not
present, so none of them can ever have passed.

| Case | Asserts |
| --- | --- |
| `BlockSheetTest` x3 (`taskBackedActualName…`, `standaloneActualName…`, `standaloneActualDraftNames…`) | A `block-name-input` on the Actual lane. `BlockSheet` returns into `ActivityActualEditor` for that lane, which has no such field. |
| `DayTimelineGestureTest` x6 (`cancelledDaySwipe…`, `nextDaySwipe…`, `previousDaySwipe…`, `actualBlockMoves…`, `actualBlockResize…`, `scrollingKeepsBlocksLockedToHourGutter`) | Header text and block geometry. The date title is in the semantics tree but not displayed, so this is device geometry rather than logic. |
| `DayCalendarHeaderTest#titleLedHeaderSeparatesDateNavigationPlanningAndCalendarMode` | `"Fri, August 28"` displayed (line 322). Same not-displayed signature. |
| `ActivityCorrectionTest#earlierDayEditorRejectsGapAndRequiresOccurrenceBeforeSavingOffline` | A `Start` field carrying `2025-11-01T23:30`. |
| `PlanModeScreenTest#draftPlannedBlockResizeGroovesStartImmediatelyAndExpandDuration` | A resize-groove result; fails an inline `check` at line 740. |
| `SettingsNotificationTest#deniedPermissionExplainsBothReminderStoresAndOffersBothRecoveryPaths` | `"cannot display either until notifications are enabled"`. `SettingsScreen.kt:224` says `"cannot display any reminders until notifications are enabled"`, so the copy moved and the test did not. |
| `DarkThemeScreenshotTest#recurringEditorUsesGroupedSectionsAndQuietSelections` | Scrolls to `"Server preview"`, a string that appears nowhere outside this test. |

Whether the test or the application is wrong is a product question in each case,
particularly for the geometry group: it depends on whether the date title is meant
to stay visible at this display size. The emulator profile is a stock Android
baseline, not the physical phone these were likely written against.

## Flaky (5)

| Case | Symptom |
| --- | --- |
| `ActivityTrackingTest#pendingQuestionDismissesToWaitingAndReopensOnlyExplicitlyInFocus` | `RootViewWithoutFocusException` |
| `ActivityTrackingTest#explicitUnspecifiedStartTypeFirstSwitchAndStop` | `ComposeTimeoutException` after 5000 ms |
| `ElapsedDurationDisplayTest#focusShowsHoursAndSeconds` | `Activity never becomes … "[DESTROYED]"` |
| `LogTimeFormTest#overnightDurationIsValidButReversedRangeCannotSave` | — |
| `TaskComposerDismissTest#keepEditingAfterSystemBackLeavesDraftVisibleAndUsable` | `RootViewWithoutFocusException` |

The first four failed only while the backend and frontend suites were running
concurrently and passed on a quiet machine. The fifth fails on a quiet machine too,
three times in six observations, and passes on `master`; it presses system back, so
it contends for window focus on its own.

## Load

The first full run aborted partway with
`INSTRUMENTATION_ABORTED: System has crashed`, leaving 42 cases unrun, while six
backend pytest runs and a vitest run were executing beside it. Re-running those 42
alone completed cleanly. Give the emulator the machine.

## Reproducing a subset

```powershell
python scripts/android-emulator.py gradle TOKEN connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.timebox.android.ui.day.BlockSheetTest"
```

Comma-separate several, and use `Class#method` for one case. Results land in
`android/app/build/outputs/androidTest-results/connected/debug/*.xml`, which carries
the stack traces; the console output does not always include them.
