# Consolidate Task Type branches permanently

Task Type Merge consolidates a source branch into an existing destination on web and Android: matching descendants combine, unmatched descendants move, and destination identities survive collisions. All source references transfer, including historical Blocks, completed, archived and trashed Tasks, and Recurring Task Series; source types disappear and reporting combines the work. This deliberate category consolidation differs from ordinary assignment edits in ADR-0005: unrelated classifications and existing Task/Block links remain intact.

Activity Tracking continues in the same running Actual Block with its elapsed time preserved and classification updated. The protected `unspecified` Task Type cannot be a merge source or destination. Explicit confirmation must explain that consolidation is permanent; the first version has no undo, accepting the loss of separable category membership to keep duplicate cleanup straightforward.

Only separate branches may merge; merging a type into itself, an ancestor, or a descendant is rejected. Confirmation shows source and destination, descendants that combine or move, and affected counts for Tasks, Planned Blocks, Actual Blocks, and Recurring Task Series, including inactive work.

Merging requires a connection and succeeds or rolls back as one operation. If branch structure or the destination changes after preview, the preview refreshes and requires confirmation again; newly assigned work is included without requiring another confirmation. Offline work arriving later with a merged source identity resolves to its surviving Task Type, so removing the source from the category list must not lose that identity mapping.

These semantics are accepted during design; implementation has not begun. The entry point remains open for prototyping.
