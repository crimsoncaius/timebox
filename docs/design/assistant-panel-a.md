# Android Assistant — quiet conversation

The user selected variant A on 24 September 2026 and requested implementation.
The question was whether conversation, the answer, or the plan should lead.
The selected layout keeps conversation first and the plan inline before the answer.

Primary design source: [prototype branch](https://github.com/crimsoncaius/timebox/tree/codex/prototype-assistant-panel),
commit `d6504662`, variant A in `frontend/src/features/assistant/AssistantPanel.prototype.tsx`.
The browser comparison, alternatives and sample data remain on that throwaway branch.
Implementation is tracked in [#286](https://github.com/crimsoncaius/timebox/issues/286).

The native implementation uses a compact title and temporary-conversation disclosure,
an accessible New conversation icon, compact user bubbles and open Assistant answers.
The inline plan card keeps the date, original read time and Reporting Time Zone;
start and end times stack beside Block Name and Task Type. Three rows appear initially
and expand in place. Larger text puts the full time range above the row identity.

The welcome screen has editable starter rows. A rounded multiline composer provides
explicit Send and Stop icons, accessible action names, a disabled empty Send, and the
existing 4,000-character limit. The follow-up suggestion fills the editable composer.
Incomplete attempts retain their explanation and Retry. Expiry, acknowledgement,
reset and scroll-following behavior remain owned by the existing controller and screen.

This supersedes the earlier visual treatment only. The approved card-before-answer
contract in `docs/specs/assistant-redesign-handoff.md` remains in force. There are no
backend or protocol changes and no changes to Planned Blocks on Day.

## Validation

- Android debug APK built successfully; all 322 unit tests passed.
- The existing Assistant instrumentation case now locates the accessible Send icon
  and checks the new welcome copy, editable follow-up, plan expansion and reset.
- A second case checks multiline drafting while a response is active, Stop, and reset.
- The full instrumentation APK has an unrelated compile failure in
  `BattlePlanScreenTest.kt`, which references removed Trash undo classes. An ignored
  local Gradle init script excludes that file when building the focused Assistant suite.
- Device execution and native review are pending: a new emulator could not boot with
  about 1 GB free on C:. The failed reservations were released. The user has been asked
  to assign the earlier Assistant review device or free space for a new emulator.

Changes remain on `codex/assistant-panel-a`; no merge is authorized yet.
