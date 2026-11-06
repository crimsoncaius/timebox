# Web audit resolution

All five reproduced web bugs are fixed. The original audit is retained in `findings.md`; this file records the implemented outcome after the user authorized the fixes and improvements. Application changes remain uncommitted for review. Browser launch and visual verification are coordinated by the main agent.

## Bug checklist

- [x] **W1 — stale Day response:** Day loads carry a generation, ignore obsolete success/error/loading updates, and all existing Day-response setters check the currently selected date. A delayed response for another day cannot erase the current plan. Permanent regression: `src/test/auditRegressions.test.tsx` W1.
- [x] **W2 — concurrent Subtask checks:** successful check/add results merge into current React state using functional setters, preserving other concurrent changes. Permanent regression: W2.
- [x] **W3 — unavailable recurring creation:** health/date lookup failures now display a specific error and **Retry date**. A successful retry enables creation without leaving the page. Permanent regression: W3, including recovery.
- [x] **W4 — recurring modal focus:** recurring forms and details use the shared modal focus hook, which contains keyboard focus, restores focus, makes the background inert, handles Escape, and locks background scrolling. Permanent regression: W4.
- [x] **W5 — invalid deadline silently ignored:** Task Save checks the actual visible input validity before sending a patch. Invalid date drafts show an inline error, remain visible, and focus the invalid input when Save is attempted. Permanent regression: W5.

## Improvement checklist

- [x] **U3 — save feedback and recovery:** Planned Block editors show Saving / Saved / Unsaved / Changes not saved and retain failed drafts with **Retry save**. Automatic creation failures can also be retried. In-flight saves prevent dismissal; concurrent saves are counted so one finished save cannot clear another's busy state. Actual Block and Task editors explain explicit-save behavior, retain failures, show actionable retry guidance, and block dismissal/editing during save. Day navigation and linked-Actual navigation honor the editor's busy/dirty guards. Failed metadata saves stop subsequent recording/deletion actions. Tests cover preserving a failed note and retrying it, plus protected Project save dismissal.
- [x] **U8 — accessible Project editor:** named modal semantics, focus containment/return, inert background, Escape handling, operation errors, retryable drafts, and disabled controls during Save/Delete. The shared helper is `src/components/useModalFocus.ts`; the main agent also uses it for the mobile sidebar.
- [x] **U10 — recurring terminology and conflict recovery:** user-facing “template” wording is replaced with recurring tasks/series. Series rows surface unresolved pre-planning counts. Details explain occupied slots, name the affected Task, link to its Day, and offer an explicit **Add to Ready to Plan** only when allowed by the server (Reporting Today fallback for older responses). Readiness uses the existing coordinator, observes current Tasks, preserves failure handling, and refreshes series notices after success. Future occurrences retain View day. This consumes the backend agent's `task_id`, `task_title`, `reason`, and `can_add_to_ready` fields; no implicit readiness changes are made. A permanent test covers links, current/future eligibility, and successful coordinated readiness.
- [x] **U11 — date/time input:** entering a time before its date retains that time; invalid/incomplete dates have inline guidance and HTML validity; typed min/max limits match the calendar; Task details state the Reporting Time Zone beside scheduling fields. Date picker and inspector close/delete targets are at least 44 px.
- [x] **Baseline TodayPage test failures:** the test fixture now supplies a valid unconfigured Google Calendar response. This removes the unrelated mocked 404 alert without weakening the existing placement assertions.

The main agent owns U1/U2/U4/U9/U12 and the sidebar/route-scroll fixes; they are outside this checklist except where shared components intersect.

## Validation

| Check | Result |
| --- | --- |
| Focused affected suites | 7 files, 73 tests passed (25.20 s). |
| Final full frontend suite | **53 files, 478 tests passed** (60.74 s). |
| Final lint | Passed, 0 errors; two pre-existing GoogleCalendar hook warnings remain. |
| Final production build | Passed, 133 modules transformed. |

The final full test run emitted jsdom's unsupported `window.scrollTo` warnings from the main agent's new route-scroll reset; these did not fail tests or indicate a browser issue. The raw test output is retained in `final-vitest.txt`, and focused output in `fix-tests.txt`. The five original probes are now permanent tests in `frontend/src/test/auditRegressions.test.tsx`; the report's repro copy has also been adjusted for the updated visible recurring label and retry control.

No issue, PR, commit, merge, production write, or external communication was performed by this implementation agent. Live browser verification and the running review application are handled by the main agent.
