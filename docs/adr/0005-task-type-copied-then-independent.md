# Copy Task Type onto new Blocks, then keep them independent

A Battle Plan Task or Recurring Task Series may have a Task Type; every Block must have one. Creating a Planned Block from a task copies that Task Type, or `unspecified` if the task is unclassified. After that the two diverge: changing the task does not retcon Blocks, and changing a Block does not write back to the task. Recurring Task Series type changes propagate to eligible Task Occurrences, not to existing Planned Blocks.

A live shared classification (retcon or write-back) was rejected so historical time records stay stable and a one-off Block can be categorized without reclassifying the work item. Linked Actual Blocks still cannot diverge from their Planned Block while linked.
