# JEV predictions from Task Type picker text

Status: presentation prototype, awaiting user choice. Follow-up to GitHub issue #168.
Primary source: branch `codex/prototype-jev-picker`. No production integration or native Android implementation in this prototype.

## Agreed behavior

The user accepted interpreting text entered into the Task Type picker on web and Android, including reclassification of already classified work. Keep the existing 500 ms debounce, confidence threshold of 0.8, explicit acceptance, and quiet failure behavior. The user explicitly deferred presentation to prototyping; a separate Suggested row is not an accepted requirement.

## Question

How should a semantic recommendation coexist with path matches and manual creation inside the existing task editor?

Three variants on `/battle-plan?task=11&variant=A`, using the real Battle Plan and Task Detail rendering with in-memory API fixtures:

- **A — Separate suggestion:** an independently dismissible suggestion with a Use button, above ordinary path matches. Clearest distinction, but more vertical space.
- **B — One results list:** an existing Task Type predicted by meaning appears first, with a small By meaning badge. Duplicates are removed. Compact and directly selectable, but asynchronous insertion can change result positions.
- **C — By meaning tab:** path results and the semantic recommendation have separate tabs. Prediction availability is indicated by a dot. Strong separation, but discovery and acceptance require another tap.

A follows the separation in the issue-168 spec. B and C intentionally explore alternatives to its presentation, without treating either as a settled change to ADR-0006 or ADR-0008. Ordinary path matches retain their relative ranking. No new ADR is warranted while the interaction remains undecided.

## Run and review

From `frontend`: `npm ci`, then `npm run prototype:jev`.

Local URL: http://127.0.0.1:12059/battle-plan?task=11&variant=A

Use the floating arrows or left/right keys outside text inputs to switch variants. Enter text in the Task Type search box, or use the prototype example buttons. The picker starts on a task assigned `work/deep` to demonstrate explicit reclassification.

Simulated cases: `practice piano` → `learning/music`; `music` gives both a path match and prediction; `run` → `health/exercise`; `maybe` simulates low confidence; `offline` simulates service failure. Other unrecognized phrases yield no prediction. There are no live JEV calls. Data and API edits stay in memory and reset on reload. Diagnostic state belongs only to the prototype.

Query-only context, withholding predictions equal to the current value, and clearing predictions on close are provisional prototype choices. Native Android layout, the winner's keyboard navigation, and interactions with existing name-driven recommendations remain for the implementation/design follow-up.

## Validation

Production TypeScript/Vite build passed. Browser interaction checks covered all three variants, explicit acceptance, dismissal, query changes, failure suppression, and stale-result removal. Screenshots inspected at 1440×1000 and 390×844. Narrow web presentation is not a substitute for native Android review.

Screenshots: `jev-picker-A.png`, `jev-picker-B.png`, `jev-picker-C.png`, and `jev-picker-mobile.png` beside this document.

Verdict: pending user review. No variant has been selected or merged.
