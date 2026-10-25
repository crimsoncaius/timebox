# Google Calendar import and conflicts

Design agreement for [#321](https://github.com/crimsoncaius/timebox/issues/321) and [#319](https://github.com/crimsoncaius/timebox/issues/319). The user confirmed the shared understanding and authorized implementation on 2026-10-25. All product decisions below are accepted.

## Accepted decisions — round 1

1. A conflicting Google move removes the old linked Planned Block from the active plan immediately. A pending-conflict notice retains the previous and requested times; cross-day moves have notices on both affected days. Actual Blocks are preserved.
2. Google owns the linked block's name and scheduled interval. Timebox owns Task Type, Supporting Note, and optional Battle Plan Task linkage. Unclassified imports use `unspecified`. Name and schedule changes happen in Google; v1 has no detachment into an independent Planned Block.
3. Import timed events that reserve calendar time, including recurring occurrences, tentative invitations, and events crossing midnight. Exclude all-day events, declined invitations, and events marked free.
4. Automatic reconciliation stops once the imported event's existing start time has passed. Preserve started and past Planned Blocks against later Google edits or deletions, independently of Actual Blocks. See ADR-0021.
5. Making room supports moving, shortening, or removing ordinary Planned Blocks with one preview and atomic confirmation. Moving and shortening preserve block identity and metadata; removing a block preserves its linked Task. Splitting is outside v1. A Google-linked obstruction must be explicitly left out of the plan or rescheduled in Google, not moved locally.

## Accepted decisions — round 2

6. Leaving a meeting out excludes that occurrence until Google changes its scheduled interval. Unchanged syncs, title changes, and reconnects preserve exclusion. A changed interval is eligible again: import if free or show a conflict. Excluding an occurrence does not exclude its series.
7. A pending future event imports automatically when its requested interval becomes free, after rechecking the latest event and occupancy. Resolve later postpones rearrangement rather than rejecting the meeting.
8. Cancellation, deletion, decline, conversion to all-day, or marking free removes a future event's linked Planned Block and clears its pending conflict. Preserve Actual Blocks and linked Tasks; briefly explain a visible plan change.
9. Initial connection imports only events that have not started, through the next 90 days. The horizon advances daily. Continue monitoring an imported future event moved beyond that horizon so its old slot does not remain reserved. Do not backfill past or already-running meetings.
10. A pending conflict expires when its requested start arrives. Retain a read-only explanation on the affected Day, without actionable conflict prompts or automatic insertion after the meeting starts.
11. Explicit calendar deselection or Google disconnection removes future imported blocks and pending conflicts, after an impact preview and confirmation. Preserve started/past plans, Actual Blocks, and Tasks. Temporary sync failure or revoked access instead preserves plans and shows that updates have stopped.
12. V1 supports one Google account per Timebox deployment and multiple selected calendars (wording corrected in round 4: the repository has a shared dataset, not individual Timebox accounts). Connection, selection, and imports are shared across web and Android. Switching Google accounts applies the disconnect policy before connecting the replacement.

## Accepted decisions — round 3

13. Settings lists excluded future occurrences with Include again. Restore immediately when free, otherwise create a pending conflict.
14. Task Type, Supporting Note, and Task linkage survive pending conflicts and exclusions, including reconnects. Restore them when the occurrence returns; omit linkage to a deleted Task. Never automatically relink or modify previously recorded Actual Blocks.
15. When a future imported meeting moves into the past, remove its future block without creating a historical replacement. Retain an explanation of the removal.
16. Once an imported occurrence's start is reached, its Planned Block stays frozen even if Google moves that occurrence into the future. Do not create a second block for that occurrence; a genuinely new occurrence can import normally.
17. An expired conflict with no started Planned Block becomes eligible again when Google moves it into the future. Import if free or reopen one conflict at the latest interval.
18. Two newly discovered overlapping Google events both remain pending rather than letting fetch order choose. An already valid imported block remains in place when a newcomer conflicts. Resolution explicitly shows the consequence for the other meeting.
19. Changes to relevant Google or Timebox state invalidate an open make-room preview. Apply no proposed edits until the user confirms a current valid preview. If Google cannot be checked, retain the conflict and offer retry.

## Accepted decisions — round 4

20. One Google account belongs to the shared Timebox deployment, with web and Android using the same connection. Individual Timebox accounts are outside scope.
21. Google integration requires authenticated Timebox access. Both clients initiate browser consent; the backend alone stores encrypted refresh credentials. Request read-only calendar-list and event scopes. Disconnect removes credentials.
22. Refresh approximately every five minutes while the backend runs, even with clients closed. Also provide Refresh now and refresh when opening stale Day data. Show last successful sync and actionable errors. Failed or incomplete fetches are never interpreted as deletions.
23. Correlate copies of the same meeting on selected calendars into one Planned Block using source identity information, never title/time matching. Preserve source associations. Precedence when copies disagree remains to be settled.
24. Disconnect removes credentials and future plans but retains exclusions and local metadata for reconnect. An explicit Forget retained Google import data action clears remembered choices and cached source details. Historical Planned Blocks and their local metadata, Actual Blocks, and Tasks remain intact.
25. V1 presents event name, scheduled time, source calendar, and source-event link. Do not copy descriptions, guest lists, attachments, or conferencing details into Timebox. Supporting Note remains local user text; Google provides additional event details.

## Accepted decisions — round 5

26. Selected calendars have an explicit priority order for duplicate copies. Default to the Google primary calendar first, then selection order; allow reordering in Settings. The preferred copy supplies name, interval, and eligibility, and its calendar is shown. Disagreement never creates duplicate blocks.
27. Deliberate deselection of the preferred calendar allows the next selected copy to take over, preserving local metadata and exclusions. Temporary fetch failures retain last known state. Authoritative cancellation or decline removes the future commitment instead of resurrecting it from a stale duplicate.
28. Forget retained Google import data clears cached details, exclusions, and metadata outside surviving blocks, but retains minimal source identifiers on historical blocks to prevent frozen occurrences from importing again after reconnect.
29. Resolution rechecks Google, rejects changed previews, and commits all Timebox edits atomically. Google cannot be locked against a subsequent external edit; a change after the check follows ordinary reconciliation.
30. Preserve imported events' exact scheduled instants and display them in the Reporting Time Zone. The horizon ends at the end of the 90th calendar day after Today. Include events starting inside the horizon even when they end beyond it. Reporting Time Zone changes alter presentation, not appointment instants.
31. Day shows one compact conflict notice with a count, opening a list and individual resolution sheets. Pending events stay outside the planned lane. Cross-day notices reference one conflict. Expired conflicts and removal explanations appear in an expandable calendar-update section.
32. Routine successful imports stay quiet. Moves, removals, and conflicts appear in the affected Day's calendar-update section, with brief in-app feedback when the visible plan changes. No new push notifications. Existing Planned Block Reminders follow valid imported blocks and are withdrawn on removal.

## Accepted decisions — round 6

33. The ordinary Delete action on a future Google-linked Planned Block becomes Leave this meeting out, explaining that Google is unchanged and retaining an exclusion. Historical blocks retain their existing deletion behavior.
34. Calendar priority changes and deselection affecting duplicate sources preview affected future meetings, removals, and potential conflicts. Apply confirmed configuration and plan changes together; preserve frozen history and route conflicting replacements through the existing conflict flow.
35. Forget retained Google import data requires disconnection. When connected, offer a combined Disconnect and forget flow with one impact preview, preventing active sync from restoring forgotten data. Reconnection starts fresh except for minimal historical identity protection.
36. Pending and excluded occurrence details allow editing Task Type, Supporting Note, and Task linkage independently of placement or Include again. Choices persist for later placement; metadata editing itself creates no Planned Block or recorded activity.

## Existing constraints

The integration is one way, Google to Timebox. Imported blocks appear in the existing planned lane and obey the no-overlap rule. Import creates no Actual Blocks, Battle Plan Tasks, or Task Completion. Web and Android share the backend behavior.

Every Day already spans midnight to midnight (ADR-0020); there is no configurable visible-day-window policy to settle. Exact intervals already support Planned Blocks crossing midnight (ADR-0019).

## Review outcome

No product questions remain open from this interview. Final confirmation of shared understanding is pending. This document does not authorize implementation, changing GitHub issues, creating a pull request, or merging.

Implementation planning must preserve durable source identity and historical freeze protection independently of source-cache refreshes, exclusions, and block deletion; serialize reconciliation with local changes; and document Google consent setup, backend secrets, redirect URLs, and authenticated client access. These are engineering follow-through on the accepted behavior, not new product alternatives.
