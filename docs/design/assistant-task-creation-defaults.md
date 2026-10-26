# Assistant task creation defaults

Design interview, 26 October 2026. The user accepted the consolidated design and
authorized implementation. Implemented on codex/assistant-creation-defaults;
the user subsequently approved committing and merging into master.

## Accepted decisions

- Scope this refinement to creating new Tasks. Editing existing Tasks is a
  separate design decision.
- Defaults mean the application's predictable Task creation behavior. The
  application supplies them; the agent supplies requested additions or overrides.
- Agent inferences beyond defaults remain visible for review. An inferred
  Project or deadline is not a default that can be hidden.
- A creation card shows the title and nondefault values, plus explicitly
  requested default values as acknowledgment. For example, "Buy milk with no
  deadline" shows "No deadline", while "Buy milk" need not.
- The agent omits unrequested defaults but includes explicitly requested values,
  even when they match defaults. This supersedes the initial allowance to omit
  explicitly requested defaults: existing optional mutation fields preserve the
  request without a separate presentation-evidence mechanism.
- Review details exposes the complete resolved Task, including implicit defaults.
- Cards show meaningful derived effects without requiring redundant agent output.
  For example, a blocking reason implies a blocked Task; the card shows the blocked
  state together with the reason even if only the reason was supplied.
- Requested descriptions appear within the creation card. Long descriptions use
  Show more to expose the complete text; creation confirmation remains on the card
  without a mandatory separate description-review screen.
- Users correct pending proposals through conversational follow-ups. The updated
  proposal replaces the previous one so only the latest version can be confirmed.
  Inline field editing is outside this refinement.
- After saving, retain the concise summary, show Task created, and offer Open task.
  Requested values and meaningful effects remain visible; full saved details are
  available on expansion without restoring implicit defaults to the main card.
- A pending single-task card uses Not saved yet and a Create task action. Tapping
  it is the explicit confirmation; no additional confirmation dialog is required.
- Multiple new Tasks have individual concise summaries and one Create N tasks
  action. The batch saves atomically. Users change or remove proposed Tasks through
  conversation before confirming the remaining batch.
- Sending a follow-up temporarily disables creation on the current proposal.
  A failed or stopped response leaves creation disabled until the user retries
  or explicitly chooses Keep previous proposal.
- An additive request carries pending Tasks into a combined replacement proposal.
  A clear correction replaces the relevant Task; ambiguous wording prompts a
  question. Pending Tasks must not silently disappear from the actionable review.
- Saved summaries are historical receipts of what was created. Later edits do not
  rewrite them. Open task navigates to the current mutable Task.

## Resulting flow

1. User requests one or more Tasks.
2. Assistant proposes requested values using sparse creation arguments.
3. Application resolves defaults and meaningful derived effects into an immutable
   review. The card shows title, explicitly requested values, and nondefault effects.
4. User can expand details or descriptions, correct through conversation, dismiss,
   or confirm with Create task / Create N tasks.
5. Follow-ups suspend the old proposal's creation action. Successful replacements
   preserve the intended pending set; failed/stopped responses require explicit
   recovery before the previous proposal can be confirmed.
6. Confirmation saves the reviewed set atomically and displays concise historical
   receipts with Open task navigation.

## Implementation evidence

The existing create_task schema already accepts sparse optional fields and
preserves omission in canonical operations. The server expands creation into a
full review, which Android renders without a default-value filter. The principal
source of default-field noise is that expanded review.

Current creation defaults include an empty description, open status, not ready
to plan, not blocked, and no Project, Task Type, urgency, importance, deadline,
reminder or blocking reason. Urgency and importance are unset, not medium.

ADR 0019 retains explicit confirmation of an immutable reviewed change set.
Presentation simplification must preserve that meaning and recovery behavior.

## Implementation constraints

- Message capture now suspends pending creation proposals in the same admitted
  transaction. Suspended proposals cannot execute or refresh. Successful source
  completion replaces them; explicit Keep previous proposal restores eligibility
  only after generation finishes, subject to expiry and normal confirmation guards.
- The model tool has no explicit supersede argument. Replacement creation proposals
  must contain the complete intended operation set; unsaved references are local
  to that set.
- Canonical operations retain explicitly supplied fields. Presentation must
  preserve that distinction and derive visible effects from the resolved review,
  rather than infer explicit intent from prose or default values.
- Public description payloads currently contain markers. Inline description
  expansion needs access to the full immutable review. Confirmation currently
  refetches that review and checks its hash; preserve those guarantees.
- Receipt summaries use authoritative saved values, not live Task reads.
  Description expansion must preserve historical content rather than substitute
  the current Task description.
- Existing expiry, stale-review, submission recovery and atomic confirmation
  guarantees remain applicable. This refinement does not change editing flows,
  add inline editors, or introduce implicit confirmation.

## Validation and review

- Backend Assistant checks: 100 passed, 14 existing skips. The task-operation
  checks also passed after the final pending-context adjustment.
- Android Assistant unit checks: 46 passed; debug APK built successfully.
- Focused Android device test passed for sparse creation fields, inline description,
  expanded defaults and one-step batch confirmation. On-device review also verified
  a single draft, addition of a second Task, correction retaining both Tasks, and
  atomic confirmation with two persisted Tasks and a concise saved receipt.
- Review uses an isolated SQLite database and API on port 12108. The configured
  OpenRouter key was rejected, so the local review launcher substitutes deterministic
  provider responses while retaining the real proposal, confirmation and persistence
  paths. Live model behavior remains unverified with the updated prompt.
- Review phrases: Create a task to buy milk; Also create Book dentist with no
  deadline and a description; Actually make it oat milk; Fail this correction.
  The fixture's dentist description is fixed test data, not generated by a model.
- Review device used: emulator-5596, token aceddc783a824b7fb8bab53e470bdfc7,
  storage root C:/Users/Caius/TimeboxRuntime/emulators. The app was reviewed on the saved
  two-task receipt; the reservation is released after merge. Captures and the isolated launcher are under
  artifacts/assistant-creation-review in this working tree.
