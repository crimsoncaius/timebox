# Ongoing Actual Block prototype — issue 228

Question: how can the ongoing Actual Block share the Planned and saved Actual
Block details design while retaining compact summary → expand to edit?

Run from the repository root:

```powershell
./scripts/ongoing-block-prototype.ps1 -Variant A
```

Debug-only native route: `timebox://prototype/ongoing-block?variant=A` (also B, C).
The bottom arrows switch variants. Planned / Actual / Ongoing controls compare
the sample states; Edit details expands the form. Stop previews the saved state.
All edits are local, with no backend dependency or writes.

- A — Shared sheet: the saved block's large time range and field ordering,
  with a compact identity and note summary before editing.
- B — Live strip: identity first, with started time and elapsed time in a
  separate running strip; the expanded fields use the shared sheet treatment.
- C — Details group: time first, with supporting details grouped behind an
  expansion and Switch / Stop below.

The native prototype follows the existing debug Activity convention and uses
the app's theme and Task Type picker. It uses a sample Day heading rather than
the live Day repository so comparison and Stop cannot mutate recorded activity.
Planned and saved samples are visual references, not full production editors.

Verdict: awaiting user review. A is the initial recommendation because its time
hierarchy and details layout are closest to the existing saved block sheet.
No variant is approved for production yet. Preserve this branch as the primary
source and implement the selected design separately.
