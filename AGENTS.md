## Agent skills

### Issue tracker

Issues and specs are tracked in GitHub Issues using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

State, type, and scope labels are defined in `docs/agents/triage-labels.md`.

### Autonomous mode

When `TIMEBOX_AGENT_MODE=autonomous` is set, follow `docs/agents/autonomous.md`; it replaces the review and merge sections below. The Grok bots' side of the pipeline is `docs/agents/grokbot-intake.md` and `docs/agents/grokbot-review.md`.

### Domain docs

This repo uses the single-context domain-doc layout. See `docs/agents/domain.md`.

### Review launches

After fixing an issue, launch or relaunch the affected application from the updated working tree and leave it running in a reviewable state. The fix is not complete until the launched instance reflects the change.

### Review and merge workflow

After implementation and validation, leave the changes on their branch with the application running for user review, then wait for the user's explicit instruction to merge into `master`. Create a pull request only when the user explicitly requests one, including draft pull requests. An instruction to implement or merge does not authorize creating a pull request.

When the user finishes reviewing, or after their requested merge into `master` succeeds, stop the review's Android emulator and confirm it stopped, following the doc that opened it (below). Do this before the merged worktree is removed; nothing stops the emulator afterwards.

### Android emulator ownership

Open, install on, launch, and review Android emulators through T3 Code's device tools, following `docs/agents/t3-devices.md`. Its emulators are assigned for that use. Use the ADB helper in `docs/agents/android-emulators.md` only for instrumentation tests, when the T3 device tools fail, and to recover or release a device the helper reserved. Other emulators and physical devices require explicit user assignment.

### Android instrumentation baseline

The instrumentation suite does not pass. `docs/agents/android-instrumentation-baseline.md`
records which cases already fail on `master` and which are flaky, so a run can be read
against a known baseline rather than treated as a regression.

### APK distribution

When building or uploading an APK, for daily use or for testing, follow `docs/agents/apk-distribution.md`.
