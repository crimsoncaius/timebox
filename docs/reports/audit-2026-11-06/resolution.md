# Timebox audit resolution

6 November 2026 · Branch `t3/bug-usability-audit` · Based on `ab7b736b4aa486aaa0c2e0ea470a2731a838fe5a`

All 15 defect findings and all 12 usability recommendations from the [original audit](README.md) have an implementation. Changes remain on this branch for user review. No pull request, commit, or merge was created. No database migration is required.

The original report preserves the reproduction steps and before-fix evidence. Its failing-probe expectations apply to the original commit. The fixed behavior is covered by permanent regression tests in the application test suites. Detailed implementation notes are available for the [backend](backend/resolution.md), [web](web/resolution.md), and [Android](android/resolution.md).

## Defect resolutions

| ID | Fixed behavior | Verification |
| --- | --- | --- |
| B1 | Completion reads the saved Reporting Time Zone after obtaining the Activity State lock. Past plans remain; future plans are evaluated in the same zone. | API and direct-service regressions in both timezone directions. |
| B2 | Battle Plan deadlines and recurrence use the shared reporting date, including service callers. | Overdue, preview, generation, current-occurrence and calendar regressions. |
| B3 | Archiving an occurrence preserves Habit results, checklist checks and quota history. Archived history stays read-only. | Archive/restore, tickability and historical-result tests. |
| W1 | Obsolete Day responses cannot replace the selected day's plans or error/loading state. | Delayed Day-response regression. |
| W2 | Concurrent Subtask responses merge into current state. | Two successful, out-of-order check responses retain both changes. |
| W3 | Failed recurring date lookup shows an error and Retry date. | Failure followed by successful retry enables creation without remounting. |
| W4 | Recurring dialogs contain focus, restore it on exit, and make the background inert. | Keyboard regression plus independent nested-popover review. |
| W5 | Save validates the visible date draft; invalid input stays visible and receives focus. | Invalid-deadline regression and date-range validation tests. |
| AND-01 | Superseded Task loads cannot replace the current Task or an edit begun during loading. | Delayed response and draft-preservation JVM regressions. |
| AND-02 | Superseded routine loads cannot publish either success or failure. | Delayed-success and delayed-failure JVM regressions. |
| AND-03 | Reminder scheduling retains the chosen local clock time across DST normalization. | Spring-transition 02:30 regression. |
| AND-04 | Track Task routes using Reporting Today; Daily Reminder links resolve `day/today` when opened. | Fixed-clock and DayViewModel tests; native Today deep link returned from Nov 5 to Reporting Today Nov 6. Track Task also opened Nov 6 and displayed the running record. |
| AND-05 | Every shared Settings switch has a meaningful accessible name. | Named-toggle instrumentation and native review; TalkBack speech was not tested. |
| UI-01 | Closed mobile sidebar controls are removed from focus navigation; open sidebar traps focus and restores its trigger. | Playwright keyboard/open/Escape test. |
| UI-02 | Entering another page resets the document scroll position; Day keeps its own timeline positioning. | Scroll Day, open Settings, assert its heading is visible. |

## Usability resolutions

| ID | Implemented improvement |
| --- | --- |
| U1 | Compact sticky Day date, Tracking, View and lane labels; timeline auto-scroll accounts for the toolbar. Whole-day visibility and Reset Zoom behavior remain intact. |
| U2 | Mobile Battle Plan uses full-width status sections, visible status links with counts, and collapsed filters. Desktop retains its board. |
| U3 | Editors show saving/saved/unsaved/failure states, retain failed drafts, expose retry, and block dismissal during saves. Day date changes and editor switches honor busy/dirty guards. |
| U4 | Empty Day explains that Task Types are optional and offers Create first plan directly. A regression verifies empty types → Create first plan → successful neutral-type block. |
| U5 | Android validates and normalizes server addresses, tests the entered connection without saving it, distinguishes persistence from reachability, and masks API keys with Show/Hide. |
| U6 | Android calendar conflicts use native date/time pickers, show duration and zone, validate intervals, and offer explicit Earlier/Later choices for repeated DST times. |
| U7 | Calendar assignment offers searchable Task Types and Tasks, including Project/series context for duplicate titles. |
| U8 | Shared web modal focus/error/busy behavior covers recurring and Project editors and the mobile sidebar. Android routine loading/error screens retain Close. |
| U9 | Network errors lead with understandable recovery instructions. Google Calendar offers Retry/Check again and keeps server setup details inside a disclosure. |
| U10 | User-facing recurrence wording uses tasks/series. Occupied pre-planning slots show the Task, date/time and reason, with View day and an eligible explicit Add to Ready to Plan action. Both clients use their readiness coordinator. No automatic readiness mutation or OS notification system was added. |
| U11 | Time-first date entry is retained, invalid or incomplete values are blocked, typed ranges match the calendar, and Task scheduling identifies the Reporting Time Zone. |
| U12 | Sidebar, filters, date controls, recurrence actions and close controls have larger targets. Repeated priority controls have named groups. Existing Android 48 dp switch targets are preserved. |

Visual evidence: [mobile Day](ui/fixed-mobile-day.png), [mobile task board](ui/fixed-mobile-board.png), [recurring conflict](ui/fixed-recurring-conflict.png), and [Android connection check](ui/fixed-android-connection.png). The recurring action was also exercised against the review API: the Task entered Ready to Plan and the UI confirmed success. Android's Test connection displayed “Connected to Timebox. Server access verified.”

## Validation

| Check | Result |
| --- | --- |
| Full backend suite | **903 passed, 22 skipped**; eight existing SQLite deprecation warnings. |
| Final backend contract checks | **94 passed** after the final `can_add_to_ready` response field. |
| Full web unit/component suite | **478 passed in 53 files**. |
| Final affected web checks | **42 passed** after toolbar, touch-target and shared scroll-mock refinements; independent review also passed 31 relevant tests. The added first-plan regression then passed in the final **25/25 TodayPage** run, bringing the suite inventory to 479 tests. |
| Web E2E | All **16 cases passed across the full run and focused rerun**. The initial full run had one queue-height assertion at 521px versus a 520px limit; compacting the redundant header fixed it. Six affected keyboard, navigation and zoom cases passed again after final layout refinements. |
| Android JVM | **467 passed in 90 suites**, no failures, errors or skips. |
| Android instrumentation | **11 passed**, no failures/errors/skips, across SettingsNotificationTest and RoutineScreenTest. [Raw results](android/fixed-device-tests.xml). |
| Builds and static checks | Web production build and TypeScript pass; Android production/test APKs build; backend Ruff and `git diff --check` pass. Web lint has zero errors and two existing GoogleCalendar hook warnings. |
| Design detector | Changed Android UI: no findings. Changed web UI: one suggestion against the existing navigation accent, retained to preserve the established design. |

Baseline failures were repaired without relaxing production behavior: seven migration tests now start from a genuine frozen historical schema and apply real migrations; the provider retry check no longer relies on a machine-load-sensitive timeout; web fixtures supply a valid unconfigured Calendar response; the stale Android permission-copy assertion now matches the existing text. The instrumentation baseline document records that last correction.

The emulator initially displayed a System UI ANR; the device tool's alert dismissal recovered it. The focused instrumentation run then passed, and the production APK was reinstalled and relaunched after the runner removed it. No unrelated emulator was changed.

## Running review

- Web: **http://localhost:15174/**, served from this worktree.
- API: **http://127.0.0.1:18001/health**. Reporting Time Zone is Asia/Singapore; deployment fallback is UTC, deliberately exercising the previously inconsistent setting.
- Isolated sample database: `C:\Users\Caius\AppData\Local\Temp\timebox-audit-fixes-2026-11-06\review.sqlite`. It contains a review Project, Tasks in several statuses, a planned commitment, a recurring conflict and a short test tracking record. No production data was used.
- Android: updated debug APK points to `http://10.0.2.2:18001/`; the app is installed and running on **emulator-5608**, managed **timebox-agent-15**, held for pending user review. Settings is open with the successful connection result.
- Review token: `6802ab08ed4c4800a479f7051745384c`.
- Emulator storage root: `C:\Users\Caius\TimeboxRuntime\emulators`; device files: `avds\timebox-agent-15.avd`.
- Keep the review servers and emulator available until the user's review is complete. Use the ownership helper's matching review lifecycle before cleanup. Merge into `master` only on explicit instruction.

## Validation limits

This does not claim a clean full Android instrumentation suite: unrelated known failures/flakes remain documented. PostgreSQL concurrency, production-scale performance, actual notification delivery, real Google OAuth/import, paid Assistant calls, microphone capture, physical devices, TalkBack speech, and exhaustive landscape/large-font layouts were not exercised. Calendar DST conflict conversion and assignment search were tested with controlled fixtures; live Google credentials were not used. No high-severity residual defect from the audited list is known, but these tests cannot establish that the whole application is bug-free.
