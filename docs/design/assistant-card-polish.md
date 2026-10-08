# Assistant card polish

Progressive prototype, 8 October 2026. Task-title hierarchy, Saved Tasks, multi-task
proposals, description review and results approved. Time by Task Type retained unchanged.
The approved direction is now implemented in the production Android task-card renderers.

## Accepted direction

- Task title leads proposals, results and saved-task rows, using the approved 22sp
  treatment. Action, Project and status remain secondary.
- Saved Tasks exposes Project, deadline and readiness/blocking state. Other stored
  attributes expand under Saved details; snapshot context remains visible.
- Proposals show material changes before confirmation. Multi-task proposals show
  one section per task and one confirmation for the whole set.
- Full description review shows complete before/after text with all task changes.
  Results identify each saved outcome and its correct task; completion Undo remains
  limited to eligible sole-completion operations.
- Time by Task Type keeps its current implementation. Legacy day-plan removal stays
  in GitHub issue #317.

## Purpose and coverage

Make four Assistant card families easier to scan: Time by Task Type, Saved Tasks,
Proposed Task changes, and Task change results. Preserve immutable snapshot meaning,
explicit confirmation, authoritative results and recovery, and restricted completion Undo
(ADR 0019). Existing Android typography, surfaces, and lane colors remain the visual basis.
Legacy day-plan removal is separately tracked in #317, outside this experiment.

## Round 1: what needs to be visible at confirmation?

Compare the current production renderer with a task-first proposal and compact result.
Start with completion because its material effects expose the cost of over-compression.
Task title, changed values, tracking stop, removed plan and readiness clearing stay visible.
Technical identities and exact expiry move behind Review details. A successful result replaces
the actionable review visually; the prototype does not erase underlying production history.

Create, edit and complete scenarios share the same fixtures between layouts. Confirmation,
dismissal, refresh, result checking and Undo are local simulations. Open Task explains the
destination rather than looking up fixture IDs in real data. Layout switches preserve outcome;
Reset returns to pending. Changed elsewhere and Lost reply exercise recovery presentation.
Refreshing reuses the sample: this does not model a new authoritative server review.

Debug-only in-app route:
`timebox://prototype/assistant-polish?layout=focused&scenario=complete`
Use `layout=current|focused`, `scenario=create|edit|complete`. Controls are study-only.

Question for review: can the task-first completion preview explain everything that will
change without opening Review details? Is any hidden information needed before approval?
Recommendation: task-first hierarchy, with material effects always expanded. Trade-off:
exact expiry and internal IDs take one extra tap. This preference is provisional.

## Next decisions, conditional on feedback

- Saved Tasks: which attributes make a match identifiable; duplicate names, unavailable
  rows, partial results, hierarchy, long titles and larger result sets.
- Time by Task Type: totals versus individual rows, plan/actual comparison and child
  expansion; missing actual time, empty ranges, future ranges and snapshot freshness.
- Change review: batches, mixed operations, description review, Subtasks, reopening,
  offline/expired/blocked confirmation, interruption and authoritative recovery.
- Result: historical receipt access, eligibility and conflicts for Undo, link identity.

Round 1 does not implement production data handling or cover the above cases. Later rounds
must use them as a coverage checklist independently of which visuals the user prefers.

## Round 1 evidence and review

- `scripts/android-gradle.ps1 assembleDebug` passed on 8 October 2026.
- Managed Android 36 phone: 1080×2424, density 420. Native interaction checks passed:
  layout switching, create/edit/complete confirmation, completion Undo, stale refresh,
  lost-reply result checking, and direct current/create deep-link navigation.
- Inspected light theme and dark theme at 130% text. Larger text scrolls; confirmation
  remains reachable. Restored light theme and 100% text for review. Tablet, landscape,
  TalkBack and live-server behavior have not been evaluated in this round.
- Screenshots: [completion](assistant-card-polish/completion.png),
  [result](assistant-card-polish/result.png), [current](assistant-card-polish/current.png),
  [study controls](assistant-card-polish/study.png), [edit](assistant-card-polish/edit.png),
  [dark / larger text](assistant-card-polish/dark-large.png).
- Branch: `codex/assistant-card-polish`. All UI changes are in the debug source set.
- Pending review: `emulator-5586`, token `af7e600ed01f43579e2a8c77533045d8`,
  storage root `C:\Users\Caius\TimeboxRuntime\emulators`.

Launch from this working tree:

```powershell
python scripts/android-emulator.py adb af7e600ed01f43579e2a8c77533045d8 shell "am start -W -a android.intent.action.VIEW -d 'timebox://prototype/assistant-polish?layout=focused&scenario=complete' -p com.timebox.android"
```

Review is pending; no overall layout preference has been accepted and no production cards changed.

## Round 1 refinement: task title leads

User feedback: the task name ("Send the client proposal") needs more prominence;
visual hierarchy should reflect the importance of the information.

Accepted principle: the task is the primary heading inside both the proposal and result.
Refinement: move it above action/status, set it in 22sp medium Manrope with a 28sp line
height, and group the quieter action and project/identity text beneath it. Keep material
effects readable below the divider; make the "Also changes" section label secondary.
No change to information disclosure or the simulated actions. Exact sizing remains
provisional pending visual review. This also updates create, edit and recovery examples.

Validation: debug build passed; installed on the same review device. Inspected the updated
completion preview, confirmed it to inspect the result, and checked the result in dark mode
at 130% text. The title stays prominent and wraps without truncation. Returned to the
pending preview in light mode at 100% text for review.
Evidence: [updated preview](assistant-card-polish/hierarchy-completion.png),
[updated result](assistant-card-polish/hierarchy-result.png),
[dark / larger text](assistant-card-polish/hierarchy-dark-large.png).

## Round 2: identify a saved task before opening its full record

The user accepted the revised task-title hierarchy and asked to proceed. Keep the
same 22sp title treatment for task-change previews, results and saved-task rows.

Decision now under review: which details should be visible when scanning saved tasks?
Recommendation: title, Project, deadline, and readiness/blocking state. Task Type,
Subtasks, planned dates, reminder and other stored attributes expand per row under
Saved details. Exact read time and timezone remain visible for the card as a whole.
Trade-off: more tasks fit in the conversation, but secondary stored attributes take a tap.

Debug route: `timebox://prototype/assistant-polish?layout=focused&scenario=saved`.
The Current / Task first switch uses identical fixtures. Matches includes two tasks
named "Send the client proposal", separated by Project, deadline and blocked state.
Long / partial adds a wrapping title, an explicit partial-results notice, an unavailable
identity, and a Show all control for four loaded records out of at least seven matches.
No matches represents a complete empty read; it does not imply unsaved recurring work
is absent. Open Task is still a local destination stand-in and shows the selected ID.

Question: can the two identically named tasks be distinguished without Saved details,
and is any attribute hidden there necessary for choosing which task to open?

Limitations: this experiment covers ordinary incomplete tasks with date deadlines;
it does not yet cover recurring records, sessions, completed tasks, instant deadlines,
or paginated children. Time by Task Type remains the next independent exploration.

Round 2 validation: debug build passed. On the same managed phone, verified expansion
and collapse, the selected sample task ID, Current / Task first switching, Show all,
partial and unavailable records, empty results, and dark theme at 130% text. Restored
light theme and 100% text; Saved Tasks remains open for review. No production card changes.
Evidence: [default list](assistant-card-polish/saved-tasks.png),
[expanded record](assistant-card-polish/saved-expanded.png),
[current renderer](assistant-card-polish/saved-current.png),
[long / partial](assistant-card-polish/saved-partial.png),
[unavailable record](assistant-card-polish/saved-unavailable.png),
[dark / larger text](assistant-card-polish/saved-dark-large.png).

## Round 3: compare time by Task Type — alternative rejected

Decision: keep the current implementation completely unchanged. After viewing both
screenshots, the user explicitly rejected the alternative and asked to move on.
Removed the experimental Time card component, its controls and its sample data.
The screenshots and investigation below are historical evidence, not an approved direction.
The `scenario=time` study link now falls back to Saved Tasks. Production Time cards
were never edited; their existing bars and presentation remain intact.

The user approved Saved Tasks ("excellent, nice"); keep that layout and disclosure.
Next question: does an aligned Actual / Planned comparison communicate time allocation
more clearly than the existing repeated sentences and small paired bars?

Recommendation: prominent date range, quieter card role and fixed read timestamp,
root-only totals, then aligned Task Type / Actual / Planned columns. Shares and plain
language differences are secondary. Child types expand inline and are explicitly
included in their parent, so totals never add parent and child values together.
Unplanned time is labelled; unavailable future actuals use a dash and an explanation.
Colors preserve Actual teal and Planned blue. Trade-off: numeric comparisons are easier
to scan, while the overview no longer uses proportional bars. This choice is provisional.

Route: `timebox://prototype/assistant-polish?layout=focused&scenario=time`.
Current / Time first uses identical data. Both, Actual, Future and No time are sample
selectors. Open Trends is a local destination stand-in. Past and today samples total
5h50 actual versus 6h planned; expanding Work must not change these totals.

Coverage still pending: planned-only reads, filters/weekdays, many roots, deep or missing
hierarchy levels, longer paths, tablet/landscape and TalkBack. These are not production
components; no backend contracts or production card behavior are changed.

Round 3 validation: debug build passed. Verified inline Work expansion/collapse with
unchanged root totals, the Trends destination stand-in, Current / Time first switching,
Actual-only columns, future-unavailable actuals with no misleading zero, empty results,
and dark theme at 130% text. Restored light theme/100% text and left the comparison open
on the same review device. Screenshots: [default](assistant-card-polish/time-card.png),
[expanded](assistant-card-polish/time-expanded.png), [current](assistant-card-polish/time-current.png),
[future](assistant-card-polish/time-future.png), [dark / larger text](assistant-card-polish/time-dark-large.png).
This direction was rejected; do not carry it into implementation.

## Round 4: review a batch with a description change

Accepted: the user reviewed the proposal and result and said "excellent, proceed".

Purpose: extend the accepted task-title hierarchy to a two-task proposal and its result,
preserving ADR 0019's all-or-nothing confirmation and full description review.

Sample: update the client proposal's deadline, readiness and description; create handover
notes. Each task keeps its own prominent title and changed fields. There is one
confirmation for the complete set, with no per-task selection or partial-save controls.
The preview keeps the description private. Review description opens a full-screen review
with the complete before/after text and both tasks' changes, then Confirm both tasks.
Back returns without saving. The receipt shows both saved outcomes and links to their
sample identities, without completion Undo (not eligible for this multi-operation batch).

Route: `timebox://prototype/assistant-polish?layout=focused&scenario=batch`.
Current / Task first uses the same preview, full description and receipt fixtures.
Changed elsewhere requires refreshing the whole review; Lost reply checks the batch
result instead of offering another confirmation. All state remains local and illustrative.

Question: is it clear that the description review confirms both task changes together,
and does the result make each saved outcome easy to identify?

Limitations: two short ordinary-task changes; no many-task batch, Subtask mutations,
long multi-page descriptions, live-server validation, interrupted description loads,
TalkBack focus restoration or production recovery. Existing coverage gaps remain listed
above; this round settles presentation, not implementation of the mutation contract.

Round 4 validation: debug build and whitespace checks passed. Native UI checks verified
the private preview, complete before/after description, returning without saving,
confirmation of both sample changes, separate saved outcomes, correct second-task link,
saved details, absence of ineligible Undo, stale-review refresh and lost-reply result
checking. The automation initially excluded the bottom confirmation button from its
tap bounds; correcting the harness allowed that same flow to pass without an app change.
Evidence: [preview](assistant-card-polish/batch-preview.png),
[full review](assistant-card-polish/batch-description.png),
[result](assistant-card-polish/batch-result.png).
Dark theme at 130% text was also inspected; the review scrolls while confirmation stays
available. [Dark / larger text](assistant-card-polish/batch-dark-large.png). Restored
light theme and 100% text, and left the batch preview running on the existing review
device (`emulator-5586`, token `af7e600ed01f43579e2a8c77533045d8`, storage root
`C:\Users\Caius\TimeboxRuntime\emulators`).

## Final prototype checks

The approved layouts share the existing debug study route and controls. Long description
and Review load fails are additional study controls under Multiple tasks. The long
fixture is shared with the Current comparator. The load failure happens once; retry
opens the full review. No confirmation is available while the description is unavailable.
These are deterministic sample states, not new network or mutation implementations.

Production handoff: apply the accepted hierarchy to the existing task-card renderers;
preserve their real permission, expiry, busy, offline, server recovery, receipt and Undo
contracts. Do not copy the prototype's hardcoded summaries, identities or local state.
Keep study controls and sample data in the debug source set.

Explicitly deferred to production integration: complete coverage of Subtasks, reopening,
recurring/session/completed reads, instant deadlines and pagination; many-task batches;
TalkBack traversal/focus restoration; landscape/tablet; and real permission, expiry,
offline, restart recovery and Undo conflicts. The prototype validates the visual direction
for representative tasks, not these production contracts. The Time card has no new
validation requirement because its proposed changes were rejected.

Final check results: `assembleDebug` and `git diff --check` passed. Native UI checks
verified failed-load messaging, absence of confirmation before full review, successful
retry, Back without saving, scrolling through the entire long description to the second
task, and the fixed confirmation button remaining reachable. Inspected screenshots:
[load failure](assistant-card-polish/description-load-error.png),
[long review start](assistant-card-polish/long-description-start.png),
[long review end](assistant-card-polish/long-description-end.png).
Restored the approved short batch preview in light theme at 100% text and retained the
same emulator for review. This completes the prototype pass with the limitations above.

## Production integration — 8 October 2026

Implemented the approved hierarchy in `AssistantTaskCards.kt` and connected the result
presentation to the existing callbacks in `AssistantScreen.kt`. Time by Task Type and
the activity/time renderers are unchanged. The protocol, persistence, transport and
recovery coordinator are unchanged.

Saved Tasks keeps snapshot/count/partial-result context and supports the existing
ordinary, occurrence, quota and session projections. Each row leads with its task title,
Project, deadline and readiness/blocking state; saved attributes and children expand
independently. Instant deadlines retain timezone meaning, and unavailable Subtasks do
not assume a title or checked state.

Proposals retain every reviewed field and material side effect, with expiry/identities
under Review details. Full descriptions remain behind explicit review, with the existing
keyboard handling and focus restoration. Confirmation still depends on source completion,
pending status, connectivity and recovery busy state. Check result remains prominent for
recovery states and accessible under Review details for pending proposals.

A saved receipt replaces the actionable proposal; View reviewed changes preserves the
immutable history. Results use authoritative receipt values and created-ID mappings, with
titles from the matching proposal only. They never infer identity from row order or fetch
mutable task data. Recovered receipts without a matching proposal use their saved title
when present, otherwise a Task/Subtask ID heading. Subtask links require a known parent.
Eligible completion Undo uses the existing recovery callback and server availability.

The debug comparison now labels its choices Production and Prototype. `layout=current`
shows the real production components against sample data. The earlier Current screenshots
remain the evidence of the pre-change implementation.

Validation: all 47 Assistant JVM tests passed, including receipt matching and date/instant
display checks. The initial focused Android run passed 11 of 12 tests; one timestamp-copy
assertion was updated for the readable date format. Final device results are recorded below.

Final focused Android run: **13 passed, 0 failed** (`AssistantTaskCardsTest` and
`AssistantTaskTransportTest`). Coverage includes full-review-only confirmation,
source completion and busy gating, offline reasons, description-load retry, keyboard
and large-text focus restoration, date-only completion intent, material effects,
saved result/history separation, Subtask parent navigation, unavailable child reads,
and the existing Undo callback. `git diff --check` passed. Production changes are
limited to `AssistantTaskCards.kt` and its call sites in `AssistantScreen.kt`;
`AssistantCards.kt` and `AssistantCard.kt` have no diff.

Native screenshots of the real production renderers with deterministic sample data:
[create](assistant-card-polish/production-create.png),
[created](assistant-card-polish/production-created.png),
[saved tasks](assistant-card-polish/production-saved.png),
[batch](assistant-card-polish/production-batch.png),
[description review](assistant-card-polish/production-description.png),
[dark / 130% text](assistant-card-polish/production-dark-large.png).
The final build is left on the Production/Create preview in the app for review.
The normal Assistant uses these same production components and existing live callbacks;
the review samples themselves do not write real tasks. No backend service or API contract
was changed or required for this presentation-only implementation. Broader device classes
and a fresh live-server end-to-end run were not exercised.
