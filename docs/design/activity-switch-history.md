# Activity switches across recorded history

Status: A selected and implemented on `codex/activity-switch-history`; pending user review and explicit merge instruction. Prototypes are preserved on `codex/prototype-activity-switch-history`.

## Settled decisions

- Android and web support choosing a switch time earlier than the Current Activity's start.
- The new activity replaces Actual time from the chosen instant onward, including earlier recorded activities and unrecorded gaps. Earlier portions remain intact.
- Example: A ran 09:00–10:00 and B is running from 10:00. Switching to C at 09:30 leaves A at 09:00–09:30 and C running from 09:30; B is replaced entirely.
- Dragging the switch line near the viewport edge should continue scrolling into earlier time, including previous days. Display the date clearly across midnight.
- Activity switches offer Undo through the existing Undo presentation mechanism and its existing availability lifecycle.
- Undo atomically restores every affected record, gap, and original detail. The former Current Activity continues as though the mistaken switch never happened, including time elapsed before Undo.
- Only the latest switch is undoable. A subsequent switch or a change that conflicts with restoration invalidates that opportunity; ordinary passage of time does not.
- Switching and Undo both work offline. Offline Undo must restore the local state immediately and synchronize correctly later.
- Expanded historical selection applies only to switching. Stopping tracking retains its existing bounds.
- Switching remains a quick flow: A's live After switch preview, concise impact summary and optional details, with one Switch action and no extra confirmation step. Actions scroll below the preview without covering it.

## Previous behavior

The Android switch timeline has a fixed three-hour viewport and two-hour navigation steps. Both clients and the backend also enforce historical switch bounds. Historical crossing therefore requires changes beyond the timeline UI.

Record Actual from a Planned Block already supports atomic replacement Undo that restores affected records and tracking continuity. Its separate overlap-confirmation policy is documented in `docs/specs/issue-173-record-actual-as-planned.md`; switching uses the single confirmation described above.

The shared Undo presentation offers one opportunity with ten seconds of visible exposure, platform accessibility/pause behavior, and Retry after recoverable failures. Navigation or restart does not retain the opportunity. Existing recorded-Actual restoration is server-backed; offline switch Undo requires extending the activity journal behavior, not merely reusing that endpoint.

## Throwaway prototype

Branch: `codex/prototype-activity-switch-history`.

Run `npm --prefix frontend run prototype:switch` and open <http://127.0.0.1:12064/day/2026-09-25?variant=A>. Use the floating arrows (or Left/Right outside fields) to compare:

- A — Live preview: Planned/After timeline, short inline impact summary, optional details, one Switch action.
- B — Before / after: wider Recorded/After comparison with the affected-record list always visible, one Switch action.
- C — Review step: choose the boundary, then review affected records before confirming historical replacement. Ordinary switches remain one action.

The prototype lives beside Activity Tracking, uses the existing Day route and Layout, and reuses `UndoNotice`. It uses only in-memory sample records. Its dedicated Vite mode prevents API calls from reaching a real backend. Production builds exclude the prototype route and bundle.

Try the long-activity/gap and overnight scenarios. Drag the line and hold near an edge, or enter an exact start time. Switch, advance the sample clock, then Undo within the existing notice window. The state disclosure shows records, preview, and Undo availability. Switching variants resets the fixture.

Verification: production build; browser interaction checks for held-edge autoscroll beyond the initial window, historical replacement, gap fill, exact metadata restoration and running continuity after elapsed time; all three variants; desktop and 390px layouts. Offline is a local simulation, not a persistence or reconciliation test. This is a responsive browser prototype, not an Android build.

Verdict: the user selected A, with its action row kept in normal document flow below the preview. Cancel and Switch activity must not float over or obscure the timeline. Edge scrolling should be faster and the dragged line must remain steady without flashing. The prototype uses a gradual speed increase toward the edge, reaching three times the original maximum speed, and anchors the drag handle independently from time snapping. These choices are carried into production.

## Native Android prototype A

The debug-only `SwitchHistoryPrototypeActivity` implements A with native Compose gestures, Timebox's theme, and its existing `UndoLifecycle` and `UndoNoticeHost`. It starts with the switch sheet open, uses in-memory sample records, and offers daytime and midnight scenarios. The action row scrolls below the preview. It does not implement production synchronization or persistence.

Build with `./scripts/android-gradle.ps1 :app:assembleDebug '-PreviewApplicationIdSuffix=.switchprototype' '-PreviewApiBaseUrl=http://10.0.2.2:12064/api/'`. The separate package is `com.timebox.android.switchprototype`; launch component `com.timebox.android.switchprototype/com.timebox.android.ui.day.prototype.SwitchHistoryPrototypeActivity` through the emulator reservation helper. Its configured API target is the browser prototype's rejecting middleware, isolating background app services from real data.

Built and installed successfully. Native prototype checks exercised dragging before the current start, held-edge scrolling back to 09:40, switching, and Undo restoring the three original records and running activity. Prototype screenshot: `artifacts/android-switch-final.png`. The retained emulator now runs the production review build described below.

## Production implementation

Both clients use the durable activity journal. The server advertises `switch_history_ready`; clients connected to an older server retain the old switch bound and do not offer switch Undo. No schema migration is needed: a switch stores its inverse ranges and expected affected-state digest in its existing operation intent. `undo_switch` references that operation and restores the original full records, including their identities and metadata. Replay rechecks the digest, so a conflicting remote operation delivered late cannot be silently overwritten by Undo. Unrelated earlier edits remain intact.

Web uses the existing Undo notice and native Android uses the app-wide Undo lifecycle. Offers expire with the existing presentation rules. The inverse is saved durably when Undo is selected, allowing the switch and its Undo to synchronize after a restart. An open record's elapsed time does not participate in conflict detection. Stop keeps its original bound.

The real web review runs on port 12064 and an isolated API/database on 12065 (`artifacts/switch-history-review.sqlite`). Start web with `VITE_API_PROXY_TARGET=http://127.0.0.1:12065` and Vite `--host 127.0.0.1 --port 12064 --strictPort`. Start the API with that SQLite path, `AUTO_CREATE_TABLES=1`, `ACTIVITY_TRACKING_DEV=true`, and `APP_TIMEZONE=Asia/Singapore`.

Android review package: `com.timebox.android.switchreview`, built using `'-PreviewApplicationIdSuffix=.switchreview' '-PreviewApiBaseUrl=http://10.0.2.2:12065/'`. Review device: `emulator-5652`, reservation `62e18fe2b40345719edbb57297adf71a`.

Validation includes backend reconciliation, planning, reporting and check-in tests; web activity/Undo tests and production build; Android unit tests and debug APK build. Live browser and native checks verified historical switching and exact restoration after server acknowledgement. A live browser offline switch → Undo → reload → reconnect also restored the exact original records. Edge-drag sampling kept the white line at a constant screen position while advancing through hours of history. Backend regression tests cover overnight replacement, notes, gaps, idempotence, offline command chains, and remote conflicts arriving before or after Undo. PostgreSQL-only tests were skipped in the local SQLite environment.
