# Recurring pre-planning destinations

Scheduled Recurring Task Series offer No pre-planning, Ready to Plan, or Planned time on web and Android. Quotas and one-off tasks are unchanged.

Ready to Plan admits each incomplete, unscheduled occurrence on its recurrence date in the Reporting Time Zone. Opening a future planning date does not admit it early. Manual removal and scheduling consume that occurrence's automatic queue entry; subsequent refreshes do not restore it. New occurrences remain independently eligible.

Existing expiry rules apply: unfinished occurrences normally become historical after their date, unless kept overdue or protected by a future Planned Block. Changing a series does not newly admit historical or completed occurrences.

Destination changes apply to eligible current and future occurrences. Leaving Planned time removes untouched generated blocks, preserving customized blocks. Leaving Ready to Plan removes automatic entries, preserving explicit readiness choices. Switching to Ready to Plan admits today's eligible occurrence immediately.

Planned time retains occupied-slot behavior: report unavailable configured slots and retry them on subsequent synchronization while eligible. There is no automatic queue fallback.

The API exposes `preplanning_mode` as `none`, `ready_to_plan`, or `planned_time`. Planned time requires `preplanning_schedule`; the other destinations have no schedule. Older clients that send only a schedule retain their existing meaning. Migration 032 classifies existing active slot configurations as Planned time, otherwise No pre-planning.
