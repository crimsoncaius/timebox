# Assistant task interactions — disposable prototype

Question: can the user distinguish a historical Task read, a proposed change, and an authoritative saved result inside the existing Android Assistant conversation?

**Status: approved through live user review on 2026-10-04.** The user accepted all three presentation recommendations: “this is all excellent, please proceed”. This is an asset for [Prototype Assistant task cards and confirmation interactions](https://github.com/crimsoncaius/timebox/issues/304), within [Map Assistant access to Battle Plan Tasks](https://github.com/crimsoncaius/timebox/issues/300). Production implementation remains outside this Wayfinder effort.

## Open

The self-contained [TaskAccess.prototype.html](../../../android/app/src/main/java/com/timebox/android/ui/assistant/TaskAccess.prototype.html) can be opened directly in a browser. Fonts are embedded from the app's existing resources. There are no network requests, model calls, dependencies, persistence, or real Task writes.

The optional loopback preview is currently available at <http://127.0.0.1:12082/>. To restart it from the repository root:

```powershell
python android/app/src/main/java/com/timebox/android/ui/assistant/serve-task-prototype.py
```

The helper serves only this HTML file and binds to loopback. The port is reserved for this worktree's `assistant-task-304-prototype` preview. Stop its process when review is finished.

## Review walkthroughs

Use the left-side scenario tabs and their numbered buttons, or interact with the center conversation. The right side exposes simulated state and failure controls, outside the proposed app UI. Each scenario resets its sample data. The composer accepts only the canned deadline correction and freshness follow-up; it is not connected to a model.

| Walkthrough | Interaction being evaluated |
| --- | --- |
| Find and open Tasks | Three initial rows, Show more, long titles, empty results, navigation to current details |
| Partial and unavailable results | Explicit incomplete results and unverified readiness; unavailable detail preserves the old card |
| Reopen a completed Task | Fresh confirmation explains that plans, tracking, readiness and reminders are not restored |
| Complete a Task | Inline consequences, confirmation, authoritative result, narrowly scoped Undo |
| A deadline changes elsewhere | Stale confirmation applies nothing, revised preview requires fresh confirmation, original read is preserved |
| A reply goes missing | Unknown outcome offers Check result; recovery returns the original saved result |
| Create with Subtasks | Task and every Subtask previewed together; simulated failure rolls back the whole set |
| Record an earlier completion | Date-only display; tracking and all Plans remain untouched |
| Review a description edit | Card names the changed field; full before/after text and confirmation live in a separate review dialog |
| Read recurring work | Saved occurrence, quota progress and Session Tasks; read-only and missing-record caveat |
| Pending, offline or expired | Response completion, offline/sync gates, navigation, replacement, dismissal, expiry and interruption |
| Old result, fresh answer | Automatically refreshed known Task informs new prose without rewriting the earlier card |

Theme, large text (150%) and compact width (360 CSS pixels) controls allow a rough accessibility/layout review. Normal sample width is 412 CSS pixels.

## Accepted presentation decisions

1. Keep task results compact and dated, with three initial rows and expansion. Opening a row navigates to current Task details; the earlier result remains historical.
2. Keep ordinary change previews inline, with every target and material side effect visible before one confirmation for the complete set. Show the authoritative receipt as a separate section, preserving the read snapshot and original proposed diff.
3. Review description text in a dedicated full-height dialog with before/after text and confirmation there. Do not put description contents in Task cards.

These presentation choices were accepted together after the user reviewed the prototype. The behavior contracts were already agreed in [task discovery and reads](https://github.com/crimsoncaius/timebox/issues/301#issuecomment-5975824468), [ordinary changes and side effects](https://github.com/crimsoncaius/timebox/issues/302#issuecomment-5975905998), and [confirmation and recovery](https://github.com/crimsoncaius/timebox/issues/303#issuecomment-5975957264). The authoritative resolution is recorded on the prototype ticket; its remaining native-validation limits carry into the final specification.

## Validation and limits

Both inline scripts parse. Manual browser checks covered completion and limited Undo, stale rejection, lost-reply recovery, grouped rollback, description review, draft/offline/expiry gates, partial/unavailable reads, reopening and recurring Session rows. No browser console errors were reported. The compact dark description dialog was visually checked at 150% text; its text scrolls and its footer actions remain available.

- [Completion preview screenshot](completion-preview.jpg)
- [Compact dark description review screenshot](description-dark-large.jpg)

This is a browser approximation using the current Android Assistant's typography, colors, cards and navigation. It does not validate Compose, TalkBack, Android Back, keyboard/IME behavior, native layout, transaction safety, persistence, operation identities, real sync, or recovery across process restart. No emulator was used. Task Detail and other app destinations are navigation stand-ins, not complete screens.

The simulated clock advances only through the demo control. Proposal history is abbreviated to labels; production must retain full immutable revisions and receipts. New-conversation invalidation is demonstrated without constructing a full conversation browser. Sample Task details are illustrative. The pure state-transition script explains selected interactions and must not be treated as production execution logic.

Source context: `AssistantScreen.kt`, existing Assistant Cards, `DESIGN.md`, and the resolved input decisions linked above. This artifact stays on the throwaway branch `codex/prototype-assistant-tasks-304`; no production UI or API is changed.
