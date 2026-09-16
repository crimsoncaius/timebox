# One Task Type picker for every assignment

Wherever the user assigns a Task Type — Planned Block, Actual Block (including Activity Tracking and Current Activity), Battle Plan Task compose and detail, Recurring Task Series — use the Planned-block typed-path interaction: free typing including `/`, filtered suggestions while focused, ancestor/leaf chrome per row, create-on-commit of the repaired path, and an ancestor hint when prefixes would be minted. Battle Plan filters and the Task Types management page are exempt. Battle Plan title shortcuts stay project, urgency, and importance; they do not set Task Type.

Chip menus, flat selects, and search-over-flat-names were rejected because they hide Task Type Path structure and cannot create. Empty-query ranking is ADR-0006; typed matching and canonicalization are ADR-0007.

On Battle Plan Task and Recurring Task Series pickers, Unset is a list row (current type first if any, then usage ranking, then Unset last). Choosing it saves no Task Type. Unclassified task and series labels say Unset, never `unspecified`. An already-stored `unspecified` on a task is shown as Unset and persists as Unset the next time the field is saved. Block pickers keep `unspecified` last instead.
