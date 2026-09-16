# Rank empty Task Type suggestions by lifetime Block usage

When the picker query is empty, suggestions are ordered: current value first if any, then account-wide Planned Block and Actual Block counts (`usage_count`) descending, then name. That Block count is the ranking on every assignment surface, including Battle Plan and Recurring Task Series; per-surface task/template counts were rejected so ranking cannot diverge. `unspecified` is excluded from the usage ranking and placed last on Block pickers so the filler stays selectable without dominating. Typed queries sort by path-match score then name, not usage.

Recency of picker commits (MRU) was rejected: it is device-local and measures last touch rather than how much recorded time a type already carries. This matches Android's Day-picker popularity metric and deliberately drops GitHub issue #195's MRU goal.
