# Optional Focus (#153)

Gated by the existing Activity Tracking development flags. Focus is device-local; the activity journal remains shared. No production migration or deployment is involved.

Web `FocusHost` sits inside ReadinessProvider around the existing routes. Hidden routes stay mounted to preserve drafts; their subtree is hidden and inert. `TodayPage` publishes planning state in a layout effect, including placement, planned drafts, dirty planned editing, dragging and pending saves. The controller invalidates pending activation when planning starts. Rejected activation is never deferred. Ordinary ActivityTracking remains usable. Restored preference is shown only when a durable current activity exists; preference alone never starts a record.

Android's application owns FocusController. `DayUiState.focusPlanningBlocked` covers explicit planning, drafts, saves and planned editing sheets. Entry checks the latest ViewModel state, and reconciliation rejects restoration while planning. A start publishes Focus through the repository's `onPersisted` callback before network delivery, with a generation guard against cancellation. Focus remains local across process restart with no expiry. A synchronized stopped projection exits it.

Both dedicated Focus screens use the real ActivityTracking controls with `focus=true`. Stop is absent. Exit is outside scrolling content and leaves recording running. Android's Focus BackHandler exits; a ModalBottomSheet owns Back first. Task/Subtask actions use the existing Task APIs and native TaskCompletion module; completion/Undo refresh Day and readiness, independently of recorded time.

## Original-start classification

`describe` is a durable instant command targeting an unknown Current Activity at its original start. Backend admission requires the known running identity, original start and Task Type. Replay retains its provenance/ID, original start and correspondence. The ordinary latest-made-change range policy still applies. Both client journals project descriptions offline and replay the immutable envelope. The web prompt permits optional Block Name; native prompt asks only Task Type and timing. `Start now` is an ordinary switch and retains earlier unspecified time.

## Wake lifecycle

Web `observeFocusWake` calls the real Screen Wake Lock API only for visible Focus with its device-local setting on (default). Visibility loss, exit and disabled settings release it. Async requests carry a generation so a stale acquisition releases itself. Return to visible Focus reacquires; unsupported, denied and browser-released requests produce quiet copy without blocking Focus. Android sets the Compose host View's `keepScreenOn` only while Focus is RESUMED and the preference is on, clearing it on pause/disposal. Neither implementation bypasses manual/security locking or battery policy. Ordinary tracking never requests wake.

## #154 integration seam

Render the inactivity presentation in both ActivityTracking components so it follows Day and Focus. Preserve the `focus` flag through every prompt/switch sheet and omit Stop throughout Focus. Android's check-in ModalBottomSheet must dismiss to its waiting affordance before the enclosing Focus BackHandler exits. The unknown prompt is distinct from inactivity and remains inline. New prompt controls must not bypass repository command durability or Focus generation/planning guards. Keep current-activity identity when responding to a stale sheet.

## Verification

Evidence is in `artifacts/activity-153-*`: full backend 282 passed/3 skipped, full web 269 passed before the extra refusal test, PostgreSQL command/reconciliation/planning 39 passed, native focused repository/controller tests and four Compose ActivityTracking tests passed. Full Android repeats the existing legacy WorkMode restoration assertion and coroutine timeout; its exact worker was stopped after capturing that failure, then focused suites ran cleanly. Final focused web checks passed 18 tests, and the final native repository/controller plus four Compose tests passed. Both rebuilt apps are left in Focus recording reading.

Real emulator checks observed a SCREEN_BRIGHT_WAKE_LOCK attributed to the development app while Focus was visible, no screen wake in Home, and reacquisition on return. Process restart and manual lock/unlock retained Focus and the activity. A real browser sentinel was acquired; generation/release/visibility failures have deterministic tests. Automation's separate browser pages did not become hidden during a new-page experiment, so that experiment is not presented as real hidden-tab verification. Physical-device battery policies and unsupported browsers remain platform limitations, not recording failures.

The final cross-device UI check stopped recording through Android ordinary Stop; web exited Focus on sync. Entering web Focus while off started immediately. Classifying reading from the original start retained Actual ID 162596304 and start 2026-09-11T08:49:02.436000Z. Android remained outside Focus until explicitly entered. Browser sentinel acquisition and release on Exit were observed against the real API with recording still running.
