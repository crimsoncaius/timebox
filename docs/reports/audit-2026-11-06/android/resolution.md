# Android audit resolution

The original [findings](findings.md) and red-probe XML remain historical evidence. Implementation changes are in `android/`; the parent agent owns emulator installation, focused instrumentation, and final visual review.

## Completed implementation

| Audit item | Resolution |
| --- | --- |
| AND-01: older Task detail responses replace current work | Superseded loads are cancelled; publication requires an active coroutine and the current Task identity. Refreshes retain an edit begun while the read was pending. The original delayed-response sequence is now a passing regression. |
| AND-02: older routine responses replace current work | Editor and lifecycle/list reads each own cancellable jobs. Cancellation is checked before success/error handling, including Task Type loading and new-series initialization. Delayed success and delayed failure regressions pass. |
| AND-03: DST changes a Daily Reminder's saved clock time | Tomorrow is reconstructed from its local date plus the chosen reminder time. Worker inputs preserve that chosen time separately from the normalized scheduled instant. The 02:30 spring-transition regression passes. |
| AND-04: device-date navigation | Track Task derives its route from the tracking clock and Reporting Time Zone. Daily Reminder links request `day/today`; Day resolves that request against server Today when opened, even after browsing another date. Fixed-clock route tests and a real DayViewModel navigation/load test pass. |
| AND-05: unnamed Settings switches | The shared switch requires a meaningful label. Every production caller supplies one, including Focus, check-ins, Daily Reminders, Planned Block Reminders, appearance, and completed-task filtering. Settings instrumentation asserts toggle semantics/names. |
| AND-U1/U2: calendar conflict time entry and ambiguous times (combined report U6) | Conflict drafts reuse the existing native date/time chooser. Repeated times require Earlier/Later selection, with UTC offset shown in the choice and review. Duration and invalid-interval feedback appear before preview; impossible/reversed intervals disable it. Tests cover both fall-back occurrences, the spring gap, and midnight crossing. |
| AND-U3: calendar assignment search (combined report U7) | Task Type assignment uses the shared searchable picker, including create. Task selection searches title and Project/series, displays that context, and excludes completed Tasks and quota aggregates. Tests distinguish identical Task titles in different Projects. |
| AND-U4/U5: connection setup (combined report U5) | Save validates and normalizes HTTP(S) addresses before persistence, rejects malformed addresses/embedded credentials/query strings, preserves entered values on failure, and reports settings persistence separately from reachability. Test connection probes the entered address/key without saving them. Keys are masked by default with Show/Hide and password keyboard behavior. The placeholder matches port 8001. A real loopback HTTP test verifies accepted/rejected keys and unreachable-server feedback. |
| AND-U6: routine loading/error escape (combined report U8) | Loading and error states retain a visible Close routine action within the sheet. Focused instrumentation covers both states. |
| AND-U7: small-control accessibility | The original device evidence established 48×48 dp effective switch targets, so their visual dimensions remain unchanged. New DST occurrence choices expose one labeled 48 dp radio row each rather than separate unnamed radio and text actions. |
| Combined report U10: failed recurring pre-planning | Series rows surface an attention count. Details show Task, intended date/time, occupied-slot explanation, View day, and Add to Ready to Plan only when the server says that action is currently eligible. Readiness uses the shared coordinator, waits for confirmation, surfaces failures, and refreshes the series. The server-provided `can_add_to_ready` avoids offering the action for future/already-ready occurrences. A regression covers delayed confirmation and rejected recovery. |

## Validation

- `testDebugUnitTest`: **467 passed in 90 suites**, zero failures/errors/skips. Summary: [fixed-unit-suite-summary.json](fixed-unit-suite-summary.json).
- Original bug probes were promoted to `AuditRegressionTest`; new tests cover stale failure, Reporting Today navigation, DST conflict conversion, task disambiguation, connection validation/probing, and explicit pre-planning recovery.
- Production debug APK built for `http://10.0.2.2:18001/`: `android/app/build/outputs/apk/debug/app-debug.apk`.
- Changed native UI files passed the Impeccable mechanical detector with `[]` findings.
- `git diff --check -- android` passed.
- Focused instrumentation is compiled separately; the parent agent will run `SettingsNotificationTest` and `RoutineScreenTest` on its owned emulator, then relaunch the production app for review. No claim of TalkBack speech testing is made here.

## Review boundaries

Parent verification completed: both focused device classes passed **11/11 tests**; see [raw results](fixed-device-tests.xml). The production APK was reinstalled and relaunched afterward. Native Today deep-link and Track Task checks opened Reporting Today (November 6), and Test connection displayed its success message. The emulator is now on a persistent review hold; the [combined report](../resolution.md) records ownership and review URLs.

No device operations, commits, pull requests, or merges were performed by this subtask. The application must still be installed/relaunched from the updated APK by the parent before overall completion. Backend notice eligibility and persistence are implemented by the backend task; Android renders that contract without inferring the current date from the device zone.
