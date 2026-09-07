# Issue 87: Plan Mode and Work Mode separation

Source: https://github.com/crimsoncaius/timebox/issues/87

## Confirmed decisions

- Web and Android should follow the same behavior for Plan Mode and Work Mode separation.
- Finish or cancel Plan Mode before entering Work Mode. Exit Work Mode before planning; the two modes must not remain active together.
- Reject conflicting transitions without automatically saving or discarding planning changes, or ending Work Mode.
- Keep the Work Mode action visible but disabled during Plan Mode, with the explanation: "Finish planning to start Work Mode."
- Preserve "Plan something first": after successfully saving a Planned Block that is current or begins within ten minutes, finish planning and then automatically enter Work Mode. Cancelling or failing to save must not start Work Mode.
- On web, planning includes selecting a Ready to Plan task for placement, dragging a task onto the calendar, creating a Planned Block, editing a Planned Block with unsaved changes, and pending planning saves. Viewing the calendar, task queue, or a saved Block alone does not count. Do not add a new explicit web Plan Mode.

## Current implementation findings

- Work Mode presents a full-screen execution surface. On Android, starting it during Plan Mode can leave the planning session active underneath that surface.
- Android has an explicit Plan Mode session. Web has planning interactions but no equivalent explicit Plan Mode state; use the boundary defined above.
- Android planning drafts do not survive process restart. Initial asynchronous Work Mode restoration can race with planning entry, so transition guards must cover initialization as well as user actions.

## Acceptance criteria

- While Android Plan Mode or a web planning interaction defined above is active, neither Start nor Return to Work Mode can activate Work Mode.
- The app-level action remains visible, is semantically disabled for assistive technology and keyboard interaction, and has an accessible explanation: "Finish planning to start Work Mode."
- Enforce the rule at the transition boundary for all existing entry paths, including direct requests, entry-warning continuation, and active Actual Block selection. Blocking entry preserves the selected date, task selection, editor, drafts, and recording state; it creates no Work Mode session or entry warning. A blocked request must not execute later merely because planning ends.
- Work Mode prevents entry into planning until Work Mode has successfully exited. Rejected planning entry does not end recording. A failed exit keeps Work Mode active and planning unavailable.
- Finishing or cancelling planning restores Work Mode availability only when all applicable planning conditions have cleared. Pending or failed saves retain the restriction while planning remains active.
- The accepted "Plan something first" transition occurs only after the successful save and completion of planning, using the time planning finishes to evaluate the existing current-or-within-ten-minutes rule.
- Initial restoration and planning entry cannot produce concurrent modes. Resolve initial Work Mode restoration before allowing planning to start; do not discard drafts or silently end an Actual Block to resolve a race.
- Preserve existing scheduling, explicit Work Mode exit, recording, and background/restart recovery behavior outside these restrictions. This narrows the product spec's navigation-survival rule: it does not authorize entering planning while Work Mode remains active.

## Validation and delivery

- Verify disabled presentation and blocked entry on Android and web, including every web planning condition and availability after save/cancel.
- Verify guarded entry has no state-changing side effects, failed saves/exits preserve the restriction, and initial restoration cannot overlap planning.
- Verify "Plan something first" successful, cancelled, and failed-save paths, including the ten-minute boundary.
- After implementation, launch both affected applications from the updated working tree and leave them running for review, as required by AGENTS.md.

All product questions raised in this discussion are resolved. The user confirmed the shared understanding and authorized implementation.

## Implementation verification (2026-09-07)

- Android: all 148 unit tests pass, including blocked entry, re-enabling after cancellation, reverse exclusion, and initial restoration ordering. All 14 DayCalendarHeader instrumentation tests pass on Pixel_9a, including the disabled Work Mode action and explanation in Plan Mode.
- Web: 28 TodayPage and WorkModeExecution tests pass, covering selected tasks, drafts, unsaved edits and pending saves, direct requests, stale asynchronous activation, failed exit, and successful/cancelled/failed "Plan something first" flows. Production build and focused ESLint checks pass.
- Live checks: the running desktop and mobile web layouts display the disabled action and explanation. Pressing the disabled action preserves the draft, and cancelling the draft restores availability. The installed Android application is running in Plan Mode with Work Mode disabled.
- Review artifacts: `artifacts/issue-87-web-planning.png`, `artifacts/issue-87-web-mobile.png`, and `artifacts/issue-87-android-plan.png`.
