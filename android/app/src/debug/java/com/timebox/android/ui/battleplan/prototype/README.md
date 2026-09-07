# Issue #89 mobile move prototype

Question: should moving a Battle Plan Task use a bottom sheet, an inline card expansion, or a dedicated picker with a review step?

Throwaway branch: `codex/issue-89-mobile-prototype`. Verdict: awaiting user comparison; no production design selected.

Run from the repository root:

```powershell
.\scripts\run-move-project-prototype.ps1 -Variant A
```

Deep link: `timebox://prototype/move-project?variant=A`; B and C select the other variants. The bottom arrows cycle variants and update the activity intent URI. Switching resets sample state. Debug source-set registration keeps the activity out of release builds.

- A: visible Move to project action, searchable bottom sheet, confirmation alongside destination selection.
- B: Change project expands the existing card, retaining task details above the picker.
- C: dedicated destination picker followed by a separate move review.
- D: user-requested dropdown directly under Move to project inside the existing task menu. The project list scrolls; selection requires the inline Move button. No extra card action. The launcher defaults to D.

The real Battle Plan screen hosts fixture tasks and projects, including Admin. Moves change only in-memory project assignment. Other Battle Plan actions are stubs. The Fail next move checkbox simulates a recoverable failure. State is logged under `MovePrototype`. These hooks and variants are prototype-only and must not be merged into master.

Checked on the Pixel 9a Android emulator: debug build/install, all three layouts, B search and Android Back cancellation, A failure/retry, source-card removal and destination navigation with planned date/deadline/subtasks retained, and C explicit confirmation to Admin. TalkBack and hardware keyboard navigation have not been fully audited.

After a design is chosen, record the verdict and this branch on #89, then implement the chosen behavior separately with production state handling and accessibility verification.
