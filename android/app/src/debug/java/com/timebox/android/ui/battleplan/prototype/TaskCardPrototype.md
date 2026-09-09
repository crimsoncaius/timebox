# Throwaway task-card prototype — issue #109

Question: how can Ready to Plan remain clear and directly toggleable while taking less space than the current full-width row?

Run: `pwsh -File scripts/run-task-card-prototype.ps1 -Variant A`.
Deep link: `timebox://prototype/task-cards?variant=A` (also B and C).
Use the floating arrows or hardware left/right keys to compare:

- A: content-width chip under task metadata; explicit add/remove wording.
- B: calendar toggle in the trailing action column; read-only status beneath metadata.
- C: compact switch beside the Ready to Plan label.

All actions are fixtures in memory. Completion clears readiness; overflow actions report a preview message. State remains visible in the footer across variant switches. Debug source set excludes this from release builds.

Verdict: awaiting visual selection. A is the initial recommendation for its explicit action label. B reduces visual emphasis but needs an icon discoverability judgment; C is familiar but occupies more horizontal space. No production decision is validated yet.

The native debug activity follows the existing Android prototype convention and includes the surrounding Battle Plan heading, filters, project metadata, mixed readiness states, and a long title. It does not load live tasks or exercise transport/pending/failure behavior. Reassess those states when implementing the selected treatment.

## Second round: D–F

User feedback: A–C still allocate too much space to readiness; use a compact symbol, analogous to the completion circle.

- D: outline/filled bookmark beside overflow; no readiness row.
- E: outline/filled pin beside existing metadata; no readiness row.
- F: outline/filled queue arrow beside completion; no readiness row.

Each has a 48dp touch target, explicit accessible readiness state/action, and long-press explanation in the prototype footer. Earlier variants remain for comparison at the user's request. D is the starting recommendation; selection remains open. F may be read as Start, which is a design tradeoff to evaluate.

## Third round: G — supplied reference

Matches the supplied card's hierarchy: completion at left, title and muted metadata, overflow at top right, compact outlined readiness pill at lower right. Uses Timebox theme colors. Ready state reads “Ready to Plan →”; off state reads “Add to Plan +”. The pill toggles readiness in memory, with a 48dp touch target around the smaller visual pill. Completion and overflow remain independent. Selection still pending.
