# Issue #81 — throwaway Plan drag study

Question: When a task is held and dragged from Tasks to Plan, which presentation makes it feel like the actual duration-sized Planned Block, without duplicated cards?

The reporter confirmed that the screenshot was taken while holding their finger down. Android currently renders a destination preview and a separate small floating title card. This study explores alternatives; no production decision is settled.

Run from the repository root: `./scripts/run-plan-drag-prototype.ps1 -Variant A`.
Debug deep link: `timebox://prototype/plan-drag?variant=A` (also B or C).
Use the floating arrows or keyboard left/right to switch; each variant starts fresh.

- A: one block snaps directly into the timeline lane and five-minute positions.
- B: one lane-sized block follows the finger, with a text-free outline showing the destination's full width and duration. The outline is quieter than the carried block.
- C: the task rail disappears during a hold and the timeline expands for placement.

Hold the task in the rail or an existing block, then drag. Try the 30/60-minute control. Release outside the timeline or over an occupied interval to cancel. Reset restores the sample day. Ordinary blocks have quiet outlines in all variants.

All data is in memory. The header is contextual scenery. This is a native debug-only activity following the existing Day header prototype convention, rather than a web simulation of touch. It does not exercise production autoscroll, persistence, resize gestures, or production drag timing.

Validation: debug build/install succeeded; emulator screenshots inspected for all three held states; rail placement and outside-lane cancellation exercised. No prototype tests added.

The user prefers B and requested a full destination outline in place of the time line. This revision is pending hands-on feedback. Once confirmed, retain the study on a throwaway branch and record the result on #81 before implementing the chosen treatment in production.
