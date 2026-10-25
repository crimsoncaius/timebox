---
status: accepted
---

# Full-page mobile navigation

The navigation redesign replaces peer bottom tabs with a full-page directory, accepting two taps between destinations in exchange for making the application's destinations directly discoverable without forcing them under unrelated parent surfaces. The agreed direction is to launch into Day, expose the directory through a button labelled Navigate, show destinations directly under non-interactive group headings, omit pinned duplicates, and title the directory Timebox.

Day remains the launch destination and is distinct from Chronicle.

## Agreed scope and directory

Android first; web navigation remains outside this redesign. The directory exposes each destination directly under these non-interactive headings:

| Heading | Destinations |
| --- | --- |
| Daily work | Day, Battle Plan |
| Chronicle | Calendar, Trends |
| Routines and goals | Habits, Recurring Tasks, Time Goals |
| Assistant | Conversation, Review |
| Organization | Task Types |
| Preferences | Settings |

Chronicle becomes a heading rather than an additional landing page. Assistant Review retains its activity-verification and correction meaning and remains associated with Assistant, consistent with ADR 0014. Grouping Habits, Recurring Tasks, and Time Goals together does not merge their domain models or lifecycles.

Remove local sibling tabs duplicated by the directory. Keep destination-specific controls such as month navigation, date ranges, and task filters. Keep useful contextual links, including access to Recurring Tasks from Battle Plan; move the dedicated Task Types management entry out of Battle Plan to Navigate.

## State, visibility, and Back

- A single bottom Navigate button replaces the Android bottom tabs on normal destination screens.
- Opening the directory preserves the source screen. Closing it with the close control or Android Back returns to that screen unchanged.
- Selecting a destination resumes its state within the current session, including selected date, filters, scroll position, and drafts. A fresh launch starts on Day. This agreement does not promise restoring all screen state after process death.
- Hide Navigate during editing, while the keyboard is open, in Focus Mode, and on detail screens. Finishing or dismissing the interaction restores navigation. Focus Mode retains its explicit exit without stopping Activity Tracking.
- Details remain within their originating workflow: Back returns to the source list and its preserved state.
- Contextual links can open a specific target in another destination. Back returns to their source, such as Assistant Review to an affected Day block and back to the same review entry. A targeted link overrides the destination's previous selection as needed to show that target.
- Directory switches do not build an accumulating history of top-level destinations. Android Back from a top-level destination other than Day returns to preserved Day; Back from Day uses normal Android exit behavior. Detail and contextual-return behavior takes precedence over this top-level rule.

CONTEXT.md records the platform-specific surface hierarchy without changing the underlying domain concepts.
