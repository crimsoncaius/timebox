# Assistant Review correction refinement

Date: 25 October 2026

This records the design interview following `docs/design/review-flow-handoff.md`
in the authoring checkout at `C:/Users/Caius/.codex/worktrees/0620/timebox`.
The handoff's accepted queue direction and existing behavior remain the baseline.
The user confirmed shared understanding and authorized implementation on 25 October 2026.

## Accepted decisions

1. Correction prioritizes fixing a misinterpreted event. Start/Switch correction
   exposes Task Type, optional Block Name, and Started at. Stop correction exposes
   only Stopped at. It is not a general activity editor.
2. The original spoken request remains visible as secondary, read-only context
   inside correction. Editable fields contain current values. The full original
   recorded result remains in Details, keeping speech and current values distinct.
3. The primary correction action says **Save & mark reviewed**, making the
   existing atomic save-and-acknowledge behavior explicit.
4. Correction uses the shared Task Type picker's creation behavior. Creating a
   Task Type persists it independently; discarding the correction does not delete
   that reusable Task Type. This follows ADR 0008 and other Actual Block editors.
5. Task Type selection opens as a separate step. Selection returns to the same
   correction draft with the chosen Task Type; cancellation preserves the prior
   selection. Both preserve all other draft values.
6. Start/Switch correction orders heading, original speech, Task Type, optional
   Block Name, then Started at. Stop correction shows activity context and
   Stopped at. The body scrolls independently of a stable footer with **Cancel**
   and **Save & mark reviewed**. Actions remain reachable with the keyboard open
   and increased text size.
7. Returning to Review reloads content, retaining existing rows while loading.
   Failures offer contextual **Retry**. Recovery copy must stop referring to the
   removed Refresh control. No pull-to-refresh gesture is required.
8. When a save outcome is unknown, fields remain locked and footer actions become
   **Close** and **Retry save**. Explain that closing does not cancel the submitted
   change. Preserve the existing close warning and retry the exact submitted
   request. A definite rejection retains an editable draft.
9. Rows show Block Name when present, otherwise Task Type. Show distinct Task Type
   context underneath; suppress only repeated information, preserving meaningful
   ancestor paths. For example, Lunch with Task Type Meals / Lunch retains Meals
   context. Details separates **Current activity** (current name, Task Type and
   times) from **Originally recorded** (immutable original result and speech).

These decisions use the existing language in `CONTEXT.md` and preserve ADR 0014.
They do not introduce a new domain concept or warrant a separate ADR.

## Review decision

The user approved committing and merging this revision into `master` on
25 October 2026. No pull request was requested. The task's emulator and isolated
backend are to be released after the successful merge.

## Implementation and validation

Implemented on `codex/review-flow-refinement`. Correction uses `TimeboxSheet`,
`SheetTextField`, `ActivityTimeField`, and the existing `TaskTypePicker`. The shared
sheet accepts an optional dismissal guard to preserve edited and uncertain drafts.
Details opens expanded; current identity and original identity remain separate.

`assembleDebug` passed. All seven `AssistantReviewTest` instrumentation cases
passed, covering Start/Stop payloads, atomic save-and-review presentation, exact
retry after a lost reply, editable rejected drafts, picker cancellation/discard
protection, contextual load retry, and re-entry freshness. The first attempt
crashed before executing any tests during fresh emulator startup; the rerun passed.
After the final field-shape and expanded-Details adjustments, the APK was rebuilt,
installed, launched, and visually checked again. The full suite was not run.

Native inspection covered the queue, Details, correction, shared picker, light
and dark themes, 1.6x text, and keyboard-open correction with the footer reachable.
Task Type creation and saving a correction were exercised against the isolated
production backend with sample data. Earlier captures used the emulator's GMT
reporting setting; the final review uses Asia/Singapore. Captures are local under
`artifacts/review-flow/`, including `correction-final.png` and `details-final.png`.

## Review environment (historical)

These details identify the validated instance, not a current device assignment.

- Device: `timebox-agent-09`, serial `emulator-5596`.
- Reservation: `1e7f0ac1c3654d9ab59122a3861a20a1`.
- Storage: `C:/Users/Caius/TimeboxRuntime/emulators/avds/timebox-agent-09.avd`.
- Owner: `2e3f: Assistant Review refinement`.
- App: `com.timebox.android`; the updated correction sheet was left for review.
- Isolated backend: `http://127.0.0.1:12103`; device uses
  `http://10.0.2.2:12103/` through the existing `reviewApiBaseUrl` Gradle property.
- Sample database: `artifacts/review-flow/review-flow.sqlite`. Backend logs are
  in the same local directory.

To rebuild this review install, pass
`-PreviewApiBaseUrl=http://10.0.2.2:12103/`. Ordinary builds retain the standard
backend default. Resume/review/release the recorded reservation only through the
repository helper, following `docs/agents/android-emulators.md`.
