# Habit ticks are backdated Task Completions

Ticking a past day for a Habit records an ordinary Task Completion dated on that day, and a completion dated inside a Skipped Task Occurrence's recurrence period reverses the skip. We rejected a separate habit-only tick record because Battle Plan, routine details, Chronicle Calendar and Habits would then disagree about whether the same work was done. This revises ADR 0001's treatment of Skipped Task Occurrences as permanently historical: skipping still happens automatically when a period ends, but it is no longer irreversible.

## Consequences

Task Completion carries a date that can precede when it was recorded. A backdated completion must not end a running Actual Block or remove Planned Blocks the way a completion recorded now does.
