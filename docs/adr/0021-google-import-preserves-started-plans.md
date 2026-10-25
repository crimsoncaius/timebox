# Google imports preserve started plans

For Google Calendar import (#321) and conflict resolution (#319), automatic reconciliation stops once an imported event's existing start time is reached: future commitments follow Google, while started and past Planned Blocks preserve the plan that existed at the time. This deliberately allows historical plans to differ from later Google edits or deletions rather than rewriting planning history; Actual Blocks remain independent. A frozen occurrence moved into the future does not create another block, while an unresolved conflict expires at its requested start and can become eligible again if rescheduled into the future because it never had a started Planned Block.

Minimal source identity survives forgetting retained import data so reconnecting cannot reimport frozen occurrences. The full accepted behavior is recorded in [the import specification](../specs/google-calendar-import.md).
