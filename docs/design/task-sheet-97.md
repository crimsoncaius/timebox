# Android task entry and details — issue 97

## Round 1: shared compact sheet

The user rejected the existing entry and details designs, limited this exploration
to Android, and supplied a task-app reference showing a bottom sheet over a task
list. They authorized a working prototype based on that reference.

This round asks whether a compact task-centred sheet provides a better hierarchy:
Project above the title; completion beside it; description immediately below;
compact deadline and Ready to Plan rows; optional priority, reminder and Task Type
chips; then a first-level Subtask checklist. Entry and details keep these positions.
The prototype uses Timebox's theme and real BattlePlanScreen behind the sheet.

## Provisional choices

- One visual direction for this round, with entry/details controls to compare the
  two states. Inline title editing and small field editors are experimental.
- Creation has one Add task action. Details changes update only local sample state.
- Completion remains explicit and independent of Subtask checks.
- No comments or attachments were added merely because the reference has them.

## Review

Open the sample task, change its title and Task Type, check a Subtask, and switch
to task entry. Create a task with a description and deadline. Inspect whether the
sheet makes the important content easier to find and whether the metadata chips
are discoverable. Reset restores the representative fixture. New-task drafts
survive switching to details during the current composition.

Debug routes:

- `timebox://prototype/task-sheet?mode=details`
- `timebox://prototype/task-sheet?mode=create`

Build: `./scripts/android-gradle.ps1 :app:assembleDebug`.
Acquire a managed emulator according to `docs/agents/android-emulators.md`, install
the APK through the reservation helper, then use the helper to run
`shell am start -W -a android.intent.action.VIEW -d <route> com.timebox.android`.

## Open decisions and limitations

- The user likes the direction and supplied further references. Compare inline versus whole-page
  Edit / Save after layout feedback; this prototype does not settle that question.
- Date and reminder editors are text placeholders; no scheduling or notifications
  occur. All mutations stay in memory, with no repository writes.
- Recurring Task Occurrences, actual/planned history, Blocked, Trash, recovery,
  persistence, reminder permissions and production validation need later coverage.
- Large text, long descriptions, keyboard handling and large checklists need
  review before integration. Priority has separate importance and urgency choices.
- #177 completion styling is explored here. #178 and #179 remain production
  requirements; local-state editing does not constitute fixes for their save and
  refresh bugs.
- The debug route and labelled experiment controls must not enter production.

## Verification and retained review

- Debug APK builds successfully; `git diff --check` passes.
- Installed and launched the debug routes on `emulator-5582`.
- Exercised entry/details switching, entered a title and created a local task,
  selected Research as the Task Type, and reset the fixture.
- Checked a second Subtask: the count became 2 / 3 while the parent retained its
  explicit Complete task action.
- Inspected entry and details screenshots; made the sheet opaque after observing
  background text bleed-through. Images and UI dumps are in `artifacts/task-sheet-97/`.
- This historical review device was retired during the 2026-09-26 emulator cleanup.
  Acquire a fresh managed device to reproduce the recorded checks.

## Round 2: focused editors from the additional references

The user's feedback was “Love where the design is going” and three more reference
photos. Preserve the shared task-sheet direction; explore its focused editors next.

| Reference | Inspiration carried into this round | Timebox adaptation |
| --- | --- | --- |
| [Compact composer](task-sheet-97/references/01-compact-composer.jpg) | Rounded input surface, context beside a prominent submit control, keyboard Send | Add Subtask shows its Parent Task and supports repeated entry without leaving the composer. |
| [Project picker](task-sheet-97/references/02-project-picker.jpg) | Search, broad tappable rows, explicit selected treatment | A flat Project list plus Admin, with the dedicated Project Color. Do not introduce the reference app's project sections or workspace hierarchy. |
| [Priority sheet](task-sheet-97/references/03-priority-sheet.jpg) | A focused bottom sheet with large outlined choices and clear selection | Separate Importance and Urgency rows, each with High, Medium, Low and Not set. No P1–P4 conversion. |

Project and Task Type pickers now filter locally and show an empty-search state.
Priority updates local state in place. Description, deadline and reminder editors
use the same bottom-sheet presentation; dates/reminders remain text placeholders.
The overall task layout and the entry/details relationship remain the round-one
direction. Voice input and project hierarchy are not requirements inferred from
the photos. Compact **task creation** itself remains an open future comparison:
this round applies the compact composer to Subtasks first.

Review by changing both priority dimensions, searching for a Project, and adding
two Subtasks in succession. Assess whether layered sheets preserve enough context
and whether returning to task details feels predictable. Inline versus whole-task
save remains unresolved.

Round-two verification: rebuilt and installed the APK; visually confirmed independent
Medium Importance and High Urgency selections; filtered Projects to Home and selected
it; added two Subtasks in succession (count increased from three to five). Search
keeps the same sheet height after filtering. Reset the fixture and retained
`emulator-5582` with the priority sheet open. The stock emulator presented its
floating input toolbar during typing; a full phone keyboard remains a coverage gap.

## Round 3: separate Importance and Urgency sheets

The user explicitly requested one bottom sheet for Importance and another for
Urgency. This supersedes round two's combined priority sheet. Both entry and
details now expose separate Importance and Urgency chips. Each opens only its
own four choices and changes only that dimension; the other value is preserved.
The existing scale and selection treatment remain unchanged.

## Round 4: compare when changes save

The user authorized exploring the next decision: per-field saving versus a task
editing session. The separate Importance and Urgency sheets and shared task layout
remain in both variants. The labelled comparison controls preserve the saved sample
across switches and are disabled during an edit session; finish or cancel first.

- **Per field:** choices and Ready to Plan update the saved local sample immediately.
  Text uses a field editor with Save field, avoiding saves on individual keystrokes.
  Back or dismiss after typing prompts to discard only that field's unapplied text.
- **Edit + Save:** tap Edit to start a separate task draft. Field choices and Apply
  to draft accumulate changes. Save changes commits the draft once; Cancel or Back
  on a dirty draft offers Discard changes or Keep editing. A clean or invalid-title
  draft cannot save. Discard returns to the saved details view.
- Task completion and Subtask checking remain immediate actions in the viewing
  state. They are unavailable during an edit session so they cannot accidentally
  become part of an abandoned metadata draft. New Subtasks may be added to a draft.
- Creation still has a single Add task action in both variants.

Direct debug routes use `save=field` or `save=all`:
`timebox://prototype/task-sheet?mode=details&save=field` and
`timebox://prototype/task-sheet?mode=details&save=all`.
Quote the full URI when sending it through an Android shell because it contains `&`.

Review task: change the description and Importance, then decide to keep only the
Importance change. Try both modes. Next change Importance and Urgency together and
leave without saving. Which behaviour matches what you expected to keep?

Recommendation to assess: per-field saving fits the focused sheets and quick
single-property updates; Edit + Save provides a clearer way to abandon a group of
changes. This is a comparison, not an accepted save policy. Neither variant makes
network calls or survives a process restart; failures, retries and concurrent edits
remain later integration requirements.

Verification: debug build and device launch passed. A clean Edit draft disabled Save;
changing Importance enabled it. Cancelling prompted for discard and restored High
Importance. Switched to Per field, changed a selection and dismissed with Back,
then switched to Edit + Save and completed a task-save flow. Review uses the existing
reserved emulator and local sample only.

## Round 5: accepted per-field saving; date and reminder pickers

The user explicitly chose per-field saving (“definitely per field”). This is now
the accepted direction. Removed Edit + Save, task-edit drafts, their controls and
the comparison banner. Importance and Urgency remain separate sheets. Selections
save immediately; multi-input fields (text, deadline, reminder) save together within
that field's sheet. Closing a dirty field asks whether to discard only that change.
New-task fields continue to accumulate until Add task.

Deadline and Reminder now use native Android date/time pickers, following the
existing TaskComposerSheet pattern. The containing sheets offer Today, Tomorrow
and Next week; deadlines can be date-only or include a time. Each has a remove
action through its enable switch. The sample reporting time zone is Asia/Singapore.
Calendar/time dialog cancellation leaves the field draft unchanged.

Current domain constraints (updated for #221): a Task Reminder is independent of
the deadline. New or changed reminders must be strictly in the future; unchanged
saved reminders may be in the past. Changing or removing a deadline preserves the
reminder and its delivery state. The backing sample Battle Plan card now reflects saved date and priority
values. This remains an in-memory prototype; it does not schedule notifications.

The active routes are again `timebox://prototype/task-sheet?mode=details` and
`timebox://prototype/task-sheet?mode=create`. Earlier save-comparison routes and
screenshots are historical exploration references.

Next review: set a date-only deadline, add a time, set a reminder, and cancel an
unfinished date edit. Judge whether these focused editors feel natural before
addressing compact creation, large-content states, and production integration.

Round-five verification: debug APK rebuilt and launched. Selected 19 September in
the native calendar, enabled a 09:00 deadline time, opened/cancelled the native time
picker, and saved the deadline. A reminder after the deadline displayed validation
and disabled Save; selecting an earlier date allowed Save and updated the task chip.
Captured the deadline sheet and retained emulator-5582 with that sheet open.

## Round-five approval

The user reviewed the date/reminder prototype and said “reviewed, it's good”.
Accept the deadline and reminder sheet design alongside the already accepted
per-field saving and separate Importance/Urgency sheets. That review is complete
and its emulator has been retired. This approves the prototype direction,
not production persistence, notification delivery, or the remaining coverage gaps.

Remaining work: resolve compact versus full task creation if needed, exercise
long content and important task states, then integrate the accepted design with
real data, error recovery and the related issue requirements.

## Round 6: compact creation and long-content coverage

The user authorized the next round after approving the previous review. Compare
the existing full task form with compact creation inspired by the supplied composer
reference. Compact starts with project, title, deadline and description. More details
expands the same draft to every existing field, including separate Importance and
Urgency sheets and Subtasks. Fewer details returns to compact. Both alternatives
retain their draft when switched and use one Add task action. No entry preference
has been accepted yet.

The labelled COMPARE ENTRY controls are experimental. The LOAD SAMPLE controls in
details deliberately replace the local sample: Normal or Long content. Long content
contains a long project name/title, three description paragraphs and twelve Subtasks,
including wrapped names and three checked items. Longer descriptions initially show
three lines with Read full description / Show less. Tapping the description edits it.
Subtasks have additional vertical padding so wrapped text does not crowd neighbours.
Unsubmitted Subtask text now receives the same discard prompt as other field drafts.

Direct routes:

- `timebox://prototype/task-sheet?mode=create&layout=compact`
- `timebox://prototype/task-sheet?mode=create&layout=full`
- `timebox://prototype/task-sheet?mode=details&sample=long`

Review: enter a task title and deadline, switch entry layouts, and expand More
details to add a Subtask. Confirm the draft stays intact. In details load Long
content, expand/collapse the description, scroll to the last Subtask, and complete
then reopen the parent. Check whether compact creation hides anything needed often.
Use Reset/Normal to return to representative starting data.

All accepted per-field saving, Importance/Urgency separation and date/reminder
editors are stable through this round. Production persistence, recurring-task
coverage and notification delivery remain outside the prototype.

Round-six verification: debug APK built and launched on emulator-5582. Verified
compact entry with the full software keyboard, switching layouts preserves title
and Ready to Plan, and Add task retains both values. The long sample wraps its
project/title, expands the description, and scrolls through the last Subtask while
the project header remains visible. Parent completion/reopening and larger font
scales were not exercised in this round. git diff --check passed.

The historical emulator-5582 review opened empty compact creation. A fresh device can
compare Full form or select Try details followed by Long content. Await the entry
layout preference before removing the experimental comparison controls.

## Accepted entry layout

The user chose full form as the default. Default task creation now exposes the
full field set immediately, with no entry comparison controls. The explicit
layout=compact debug route remains only as a historical prototype reference.
Separate Importance/Urgency sheets and per-field editing remain accepted.
Verification: assembleDebug and git diff --check passed. Launched mode=create without a layout parameter and confirmed deadline, readiness, separate Importance/Urgency, and Subtasks are visible; entry comparison controls are absent. Updated full form retained on emulator-5582 for review.

The user reviewed and approved default full-form entry. The combined core design
is accepted: full-form creation, per-field editing, separate Importance and Urgency
sheets, and focused deadline/reminder editors. Review reservation released.
Remaining verification/integration coverage includes parent completion/reopening,
recurring tasks, larger font sizes, and real-data save failure/retry and refresh.
These are coverage gaps, not requests to reopen accepted layout preferences.

## Production integration

The user requested implementation of the accepted design. Android creation and
details now share TaskFieldsSheet. Creation defaults to the full form; details
commit a single field at a time through the repository. Task types and projects
come from the API, and Importance and Urgency have separate sheets. Date/reminder
editors use the reporting time zone and native pickers.

Field writes update the saved response in place. Failed writes retain the field
and expose retry/discard; saved-state recovery preserves dirty drafts. Subtask
creation and checking likewise consume mutation responses without a follow-up
list reload. Parent completion/reopening retain checked Subtask states and use
the existing lifecycle service. Quota Trackers expose Session Tasks and cannot
be explicitly completed; recurring work cannot move into Projects.

Creation saves drafted Subtasks after creating the parent. The parent ID and
next Subtask index survive recreation so retry resumes after known successful
writes. If creation is partial, the existing parent is preserved and remaining
Subtasks can be retried or discarded. This retains the existing online API's
limitations for a response lost after a server-side write; it is not an offline
or idempotent transaction protocol.

Repository mutation hooks enqueue device reminder synchronization independently
of UI refresh. Completion feedback and Trash undo are hosted within the details
sheet so they remain reachable. Task sheets use the existing Project editor's
normal Back dispatcher: Back hides the keyboard first, then asks about unsaved
content without hiding a still-mounted sheet.

The accepted prototype and direct debug routes are retained as design references.
The implementation uses normal task navigation, including task notification deep
links; no experimental comparison or sample-loading controls are in production.

### Implementation validation and review

Debug app and instrumentation APKs built successfully. The focused battle-plan,
readiness and completion unit suites passed 90 tests. Twenty distinct focused
device tests passed across the integration and final Back-navigation regression
runs, covering field isolation, failure/retry, recovered drafts, partial creation,
readiness, planned dates and dismissal. The broader unit run encountered Work Mode
assertion failures and coroutine timeouts and was interrupted; it is not a passing
full-suite result.

Manual checks against an isolated API verified separate Importance/Urgency saves,
deadline/reminder persistence, creation with a drafted Subtask, completion/reopening
with checked Subtasks retained, visible completion feedback, and Subtask Trash/Undo.
Scheduled occurrences and quota Session navigation were exercised. At 150% system
text size, content wrapped and the lower actions remained reachable by scrolling;
normal text size was restored afterward.

The final navigation check encountered one input-dispatch ANR with heavy emulator
graphics-composer/RenderThread kernel activity. After restarting the app, opening
the same task and opening/closing Importance succeeded. The cause of that isolated
ANR is not established; retain it as a review observation.

The implemented task sheet was reviewed on emulator-5580 (since retired).
Reproduction uses isolated review data served on host port
12015, with the Android build override
`-PreviewApiBaseUrl=http://10.0.2.2:12015/`. Start that API when reproducing the review.
The normal project API configuration is unchanged.
