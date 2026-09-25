# The Assistant proposes tracking changes; the client applies them

The Assistant never writes Activity Tracking data. Its `propose_tracking` tool accepts only a Track (Task Type Path candidates, optional Block Name, optional start) or Stop (optional end) intent; the server validates the paths against existing Task Types, resolves stated times to instants in the Reporting Time Zone, and returns a Tracking Proposal without persisting any activity change. The client decides at confirmation whether a Track intent starts or switches, renders the live impact from local state, and applies the confirmed change through the ordinary activity journal, the same path as manual tracking.

This keeps the model's job to language (what and when), keeps date arithmetic and tracking-state reasoning deterministic, and preserves offline confirmation, journal reconciliation and the existing switch Undo without a second write path. The cost is that every Assistant tracking change needs a user tap. A future mode in which the Assistant applies changes itself must replace this decision rather than add a server-side write beside the journal.

## Considered Options

- A presentation-line `"tracking"` kind instead of a tool: rejected because schema-validated tool arguments are more reliable on the flash model.
- Converting the Assistant to an open ReAct loop: rejected as unnecessary; the respond → one tool → finish graph fits a single proposal per response.
- Deterministic server-side Task Type matching: rejected because mapping "eating" to `Meals` needs the model; the server only rejects paths that do not exist.
