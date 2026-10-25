# Assistant Review — implementation handoff

Date: 25 October 2026

## Outcome and decision status

Refine Android **Assistant → Review** using the existing Timebox visual system. The user accepted the overall proposed direction, explicitly requested removal of the redundant header content, and required reuse of the existing category-selection pattern. The **Correct activity / correction sheet needs another design pass**; its exact layout is not finalized.

This handoff records work to implement. This conversation changed only the HTML comparison and documentation, not the production Android flow. No merge or pull request is requested.

## Agreed changes

### 1. Simplify the queue header

The desired sequence is **Review title → To review / Reviewed tabs → activity rows**.

Remove both areas the user marked in red:

- The inner, repeated “Review” heading and “Check saved task changes and recorded activity.” sentence. Keep the shared page title.
- The entire queue utility row containing “Times in Asia/Singapore” and “Refresh”.

Both sides of the HTML comparison already include these removals. The left is therefore a modified reconstruction, not an untouched before-state.

Keep useful guidance in empty states and timezone context in time editing. Preserve loading, offline and sync feedback. Removing Refresh must leave a usable recovery path: retain automatic loading and provide contextual Retry on load failure. Check return-to-screen freshness against the existing retained-state behavior. Pull-to-refresh was mentioned as an option, not an agreed requirement.

Done when the queue has one Review title, tabs lead directly into content, and failures remain recoverable without a permanent Refresh control.

### 2. Reuse Timebox's existing category picker

Use the established **Task Type picker** rather than implementing the picker illustrated in the HTML. Reuse its appearance, path-aware search, ranking, ancestor/leaf treatment and selection conventions.

Read:

- `android/app/src/main/java/com/timebox/android/ui/day/TaskTypePicker.kt`
- `docs/adr/0008-one-task-type-picker-for-assignment.md`
- `docs/adr/0007-task-type-path-matching-and-canonicalization.md`

Selection returns to the same correction draft. Retain other draft values. Task Type names already contain their full slash paths; this change improves presentation and matching rather than restoring missing data. Follow the shared picker's supported integration contract; if its creation behavior requires a scope decision for correction, surface that specific question rather than inventing a second selection pattern.

Done when category assignment looks and behaves like the existing Timebox assignment surfaces, and selection/cancellation preserves the correction draft correctly.

### 3. Carry forward the rest of the proposed visual direction

Use the right side of the comparison as the direction for the queue and details:

- Give activity identity and recorded start/stop time clear reading priority.
- Remove redundant category metadata when it repeats the activity title; preserve useful category/path context when distinct.
- Keep the full spoken request visible in each row, with secondary emphasis.
- Keep Details, Correct and Looks right. Give Looks right a restrained tonal emphasis; retain quieter secondary actions.
- Preserve Timebox typography, neutral surfaces and restrained dividers.
- In Details, show enough current identity/category/time alongside the original receipt to understand what changed.
- Retain clear empty, offline, loading and failure states. Provide concise accessible completion/error feedback and contextual row actions.

The user said “everything else is good” in reference to the visual discussion. Treat this as acceptance of the overall direction, not permission to add unrelated features or implement every speculative item from the aesthetic audit.

## Open: Correct activity / correction sheet

The user's “correctivity” wording is interpreted here as **Correct activity / the correction experience**. They explicitly said it may need adjusting again. Do not treat the current HTML correction sheet as a final specification.

Before finalizing this portion, show a focused revision using actual Timebox components. Start from the existing shared sheet and picker rather than another bespoke design:

- `android/app/src/main/java/com/timebox/android/ui/components/TimeboxSheet.kt`
- `android/app/src/main/java/com/timebox/android/ui/day/ActivityTimeField.kt`
- `android/app/src/main/java/com/timebox/android/ui/day/TaskTypePicker.kt`

The previous proposal suggested a stronger heading, stable footer, clearer date/time affordances and an actual-time action color. These are starting points for the revision. Exact field arrangement, title, footer labels and picker placement remain open. In particular, **“Save & mark reviewed” is prototype wording, not a required replacement for production copy**.

Implement the settled queue changes independently; use the focused correction revision to resolve this remaining design decision with the user.

## Behavior that must survive the visual changes

- Tracking already executed; review acknowledges or corrects it. This flow has no extra tracking confirmation or Undo.
- Speech stays visible. Original receipts remain immutable.
- Saving a correction also marks the resulting revision reviewed atomically.
- Start correction edits category/name/start; it preserves an eventual end. Stop correction edits stopped time only.
- Historical date/time and DST handling remain available.
- Removed, split or otherwise uneditable targets retain their Day recovery route.
- Offline/unsynced state remains durable. Corrections and acknowledgement observe existing connectivity/sync guards.
- Rejected saves retain an editable draft. Unknown outcomes retain the exact retry request and associated edit lock.
- Edited-draft dismissal retains discard protection.
- Reviewed records remain inspectable/correctable; later changes retain their indication.
- Saved task results and recovery controls remain available even though the introductory sentence is removed.

For the existing integration contract, read `docs/design/review-queue.md` and `docs/adr/0014-assistant-proposes-tracking-client-applies.md`.

## Evidence and implementation entry points

- **Production surface:** `android/app/src/main/java/com/timebox/android/ui/assistant/AssistantReviewScreen.kt`.
- **Shared page title/navigation:** `android/app/src/main/java/com/timebox/android/ui/TimeboxNavHost.kt` and `TimeboxApp.kt` in the same directory.
- **Interactive comparison:** `docs/design/review-comparison.html`. Fonts are embedded. This is illustrative HTML, not native rendering or production logic. Its custom category picker is explicitly superseded by the reuse decision above; correction remains provisional.
- **Preview:** `http://127.0.0.1:12102/review-comparison.html` while the local server runs. Port 12102 is reserved for this checkout's `review-comparison` service. Restart from the repository root with `python -m http.server 12102 --bind 127.0.0.1 --directory docs/design` if needed.
- **Earlier assessment:** `.impeccable/critique/2026-10-25T12-50-54Z__oid-ui-assistant-assistantreviewscreen-kt-736c79c6.md`. Consult it for rationale; this handoff takes precedence where later user decisions differ.

The aesthetic assessment was source-based. Android assembleDebug succeeded, but a fresh emulator boot timed out and was cleaned up. No native visual sign-off was obtained. The HTML was inspected at desktop/mobile widths and its sample correction/category interactions were checked. PNGs beside the HTML are earlier captures and may predate the header removals; use the current HTML for those decisions.

## Validation and review handoff

1. Inspect the current checkout and applicable repository instructions. The authoring checkout was detached; create/use an implementation branch before changing application code. Preserve the HTML, critique and other existing uncommitted artifacts.
2. Apply the settled queue/header changes and integrate the existing picker. Resolve the correction-sheet design through the focused revision described above.
3. Validate acknowledgement and save-and-review, start versus stop fields, category selection/cancel, draft dismissal, reviewed records, loading/retry, offline/sync and save-failure recovery. Reuse `android/app/src/androidTest/java/com/timebox/android/ui/assistant/AssistantReviewTest.kt` where relevant. Read `docs/agents/android-instrumentation-baseline.md` before interpreting broader instrumentation results.
4. Inspect the native queue, Details, category picker and correction sheet with representative speech/path lengths, keyboard open, increased font size, and light/dark themes. Confirm actions remain reachable and result/speech hierarchy remains legible.
5. Follow `docs/agents/android-emulators.md` before operating any emulator. Historical reservations in other documents are not an assignment. Launch the updated application from the implementation branch and leave the verified instance available for user review.
6. Report what changed, checks performed, remaining limitations, and the managed review device details. Wait for explicit merge instructions; create a PR only if explicitly requested.

Completion requires the agreed visual changes, resolved correction design, preserved behavior, and an updated native app running for user review—not merely an updated HTML mockup.
