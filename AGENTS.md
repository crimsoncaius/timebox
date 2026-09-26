## Agent skills

### Issue tracker

Issues and specs are tracked in GitHub Issues using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the five default canonical triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses the single-context domain-doc layout. See `docs/agents/domain.md`.

### Review launches

After fixing an issue, launch or relaunch the affected application from the updated working tree and leave it running in a reviewable state. The fix is not complete until the launched instance reflects the change.

### Review and merge workflow

After implementation and validation, leave the changes on their branch with the application running for user review, then wait for the user's explicit instruction to merge into `master`. Create a pull request only when the user explicitly requests one, including draft pull requests. An instruction to implement or merge does not authorize creating a pull request.

### Android emulator ownership

Before using an Android emulator, recovering a reservation, or completing an Android review or merge, follow `docs/agents/android-emulators.md`. Use its helper for device commands and storage cleanup. Existing unregistered emulators and physical devices require explicit user assignment.

### Android instrumentation baseline

The instrumentation suite does not pass. `docs/agents/android-instrumentation-baseline.md`
records which cases already fail on `master` and which are flaky, so a run can be read
against a known baseline rather than treated as a regression.
