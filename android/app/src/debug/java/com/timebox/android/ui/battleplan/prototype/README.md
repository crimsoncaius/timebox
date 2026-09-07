# Task card prototype — issue #65

Throwaway native Android study: which compact Battle Plan Task hierarchy works best on phone and tablet? Launch with `./scripts/run-task-card-prototype.ps1`. Debug-only route: `timebox://prototype/task-cards?variant=A` (A–D); arrows switch variants and update the activity intent URI. All changes are in memory.

## Proposed hierarchy for this study

- Always visible: title and explicit Task Completion circle, with a 48dp touch target. Completion has Undo. Card tap opens details.
- Conditional: Blocked, deadline, and checked/total Subtask progress. None implies completion. Variant A shows these below the title; B separates the deadline into a trailing column; C initially shows only Blocked and reveals metadata inline.
- Details only: task type, Project, blocker explanation, priority, recurrence, and planning information/actions. This is a hypothesis for comparison, not an approved final hierarchy. Deadlines and Planned Blocks are separately labelled in details.

A is the closest to the supplied Todoist reference. B tests maximum density and loses individual card boundaries. C tests progressive disclosure and requires an extra tap to see dates/progress. D approximates the existing production card as a density baseline, using the same fixtures; it is not the production component. Native touch semantics and the existing Timebox theme are used. In-memory fixtures include long and bare titles, empty optional fields, Blocked, Ready to Plan, Planned Blocks, recurrence, completion, and checked-but-incomplete Subtasks.

Phone shows one status lane; tablet adds an adjacent lane. Dark/light and a state inspector are available. Completion, Undo, details, and Ready to Plan changes are interactive. Overflow menus, navigation, actual backend mutations, and final production accessibility validation are outside this throwaway study.

## Drag follow-up

The user selected A's visual direction and requested three more versions preserving drag functionality and its affordance. E adds a right-hand grip; F separates the grip into a left gutter; G uses a small grip cue and a long hold anywhere on the card. The A–D references remain available. Launch E, F, or G with the same script's `-Variant` argument. The new variants support in-memory reordering, a lifted preview, insertion indicator, edge autoscroll, and status changes by hovering a tab or dwelling at a side edge. Completion still has Undo; drop to Completed explicitly completes the task. Move up/down accessibility actions provide an alternative to dragging. The visible order and state inspector expose the result. No final drag variant or production implementation is selected yet.
