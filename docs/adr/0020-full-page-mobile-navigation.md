---
status: accepted
---

# Full-page mobile navigation

The navigation redesign replaces peer bottom tabs with a full-page directory, making the application's destinations directly discoverable without forcing them under unrelated parent surfaces. Day is the launch destination, Navigate opens the directory, and destinations appear under non-interactive group headings in a directory titled Timebox. The original single-button, two-tap choice was revised on 2026-10-25 to add optional one-tap destination shortcuts in the inset dock described below. The directory itself still lists each destination once.

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

- A floating bottom dock contains a horizontally scrollable strip of user-selected shortcuts, followed by Add shortcut, and a separate fixed Navigate item at the right. With no shortcuts, the strip still offers Add shortcut. The strip returns to its beginning when switching destinations or returning from the directory or shortcut editor.
- Opening the directory preserves the source screen. Closing it with the close control or Android Back returns to that screen unchanged.
- Selecting a destination resumes its state within the current session, including selected date, filters, scroll position, and drafts. A fresh launch starts on Day. This agreement does not promise restoring all screen state after process death.
- Hide Navigate during editing, while the keyboard is open, in Focus Mode, and on detail screens. Finishing or dismissing the interaction restores navigation. Focus Mode retains its explicit exit without stopping Activity Tracking.
- Details remain within their originating workflow: Back returns to the source list and its preserved state.
- Contextual links can open a specific target in another destination. Back returns to their source, such as Assistant Review to an affected Day block and back to the same review entry. A targeted link overrides the destination's previous selection as needed to show that target.
- Directory switches do not build an accumulating history of top-level destinations. Android Back from a top-level destination other than Day returns to preserved Day; Back from Day uses normal Android exit behavior. Detail and contextual-return behavior takes precedence over this top-level rule.

CONTEXT.md records the platform-specific surface hierarchy without changing the underlying domain concepts.

## Quick access revision (2026-10-25)

The user selected prototype C after comparing five treatments. The dock uses icons above labels and a full selected-item background. A subsequent user-requested refinement groups shortcuts in one rounded tonal surface and places Navigate in its own adjacent rounded surface, making their different roles visible. When all shortcuts are removed, Add shortcut remains available beside Navigate. A subsequent user-requested refinement makes these surfaces float over the app, with transparent space around and below them. Scrolling content continues behind the dock and includes enough trailing clearance to bring the last item above it; fixed actions stay above the dock. This replaces the original choice to reserve layout space beneath the screen.

- Day and Battle Plan are the initial shortcuts. Any directory destination can be selected, including Settings; users can remove all shortcuts.
- Edit shortcuts is accessible through Navigate and directly through Add shortcut. It supports all directory destinations without a three-item limit, removing and reordering them, and a scrollable preview. Save, Cancel, and Back return to the surface that opened the editor.
- Save commits the ordered list locally on this Android installation. Cancel and Back discard draft changes. An empty saved list remains empty across restarts. Preferences do not synchronize between devices.
- Shortcuts use the same navigation path as directory selection, including preserved destination state and Back behavior. Selecting the current shortcut does nothing. Day resumes its selected date rather than jumping to Today.
- The entire dock follows the existing visibility rules. The editor hides the originating surface and returns there after Save or Cancel.

The throwaway comparison is preserved on branch `codex/navigation-shortcuts-prototype`, commit `a6218e3d`. The implementation excludes the comparison controls and prototype routes.
