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

Before installing, launching, testing, capturing, or interacting with an Android emulator, read `docs/agents/android-emulators.md` and acquire an exclusive reservation through `scripts/android-emulator.py`. Use the helper for device commands. Acquire as many managed devices as the work needs. Release temporary devices automatically; retain devices explicitly for user review. Existing unregistered emulators and physical devices require explicit user assignment.

### Android instrumentation baseline

The instrumentation suite does not pass. `docs/agents/android-instrumentation-baseline.md`
records which cases already fail on `master` and which are flaky, so a run can be read
against a known baseline rather than treated as a regression.

### Worktree lifecycle

After a worktree branch is merged into the repository's primary branch (`master` here), remove the clean merged worktree and prune stale worktree metadata. The merge is not complete until the removed worktree no longer appears in `git worktree list`; preserve any dirty or unmerged worktree.
