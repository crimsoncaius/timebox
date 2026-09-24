# Android Assistant panel study

Question: should conversation, the useful answer, or the plan lead the Android Assistant?

Run `npm run prototype:assistant` from `frontend`, then open
http://127.0.0.1:12056/assistant?variant=A. This uses the existing web route as a
browser comparison harness for the Android surface; it is not a web Assistant
implementation. The Android header, composer and navigation context are represented
in a mobile frame, using the Android color tokens and bundled typefaces.

- A — Quiet conversation: compact controls, inline plan, open answer.
- B — Answer first: conclusion first, supporting schedule in a disclosure.
- C — Plan + discussion: a separate collapsible plan reference above discussion.

The floating arrows and left/right keys cycle variants and update the URL. Arrow
keys inside the composer edit the draft. The controls select response, empty,
interrupted and empty-plan fixtures, plus dark theme and larger text. Expand plan
rows, fill a starter prompt, Send, Stop, and New conversation to explore controls.
The state disclosure exposes sample data and current control state.

All content is an in-memory fixture. Send replaces the displayed sample exchange
and simulates a read; it does not generate a relevant answer to arbitrary input.
The bottom navigation is contextual scenery. No native code or backend behavior
is changed. Native IME, TalkBack and conversation lifecycle validation are outside
this browser layout study.

The approved baseline in `docs/specs/assistant-redesign-handoff.md` puts the plan
before interpretation. B deliberately reopens that decision. C would require a
decision about selecting historical snapshots. Neither is approved for production.

Validation: production build and focused ESLint passed. Browser review exercised
all three variants, dark/large text, reset, editable starter prompts, arrow-key
editing and simulated sending. No new automated tests, per the prototype skill.

Verdict: awaiting user comparison; no direction selected. Preserved on
`codex/prototype-assistant-panel`. After selection, record the decision and link
this branch from the implementation issue; rewrite the selected approach in native
production code. Keep this throwaway comparison out of master.
