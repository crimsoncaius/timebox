# Stop tracking timeline review

Stop tracking reuses the existing SwitchActivityTimeline component and SwitchTimelineWindow selection model. A null next activity renders a neutral Unrecorded region after the selected end, including the visible future. Both tracking controls and ongoing Actual details supply planned and recorded context and linked plan titles. Stop commands, observed target IDs, validation, and the fixed action footer remain intact.

The Switch selector and emulator ownership helper were brought forward from existing uncommitted work in C:/Users/Caius/Desktop/timebox. That checkout was not modified.

Validation: 19 focused JVM tests passed (StopTrackingPreviewTest, SwitchTimelineModelTest, ActivityRepositoryTest). Four emulator tests passed: StopTrackingTimelineTest, both ActivitySwitchTimelineTest tests, and ActivityCorrectionTest.lateStopPreviewCancelAndOfflineSaveUseDifferentActionAndEffectiveTimes. Command-flow tests use in-memory repositories.

The APK from this worktree is installed on emulator-5582. Stop tracking is left open with 21:45 selected in Asia/Singapore, without confirming. Live current activity ID, start, end, update timestamp, and server cursor were verified unchanged. Screenshot and UI hierarchy are beside this file.

Review reservation: 3cdaddf6bd2f4dbc8e22f0bf50db015c (pending user review). Resume through scripts/android-emulator.py for user-requested changes; release with --review-done after the user finishes.
