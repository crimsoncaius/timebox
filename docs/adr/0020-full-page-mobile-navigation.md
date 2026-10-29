---
status: accepted
---

# Mobile navigation shelf

The Android navigation directory is a Navigation Shelf beside the current screen. This revision (2026-10-29) replaces full-page Home and launch-on-Home: users can consult navigation without leaving their current work. Timebox retains its visual style and existing destination groups.

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

Remove local sibling tabs duplicated by the directory. Keep destination-specific controls such as month navigation, date ranges, and task filters. Keep useful contextual links, including access to Recurring Tasks from Battle Plan; move the dedicated Task Types management entry out of Battle Plan to the Navigation Shelf.

## Shelf, state, visibility, and Back (revised 2026-10-29)

- Launch on the last main destination with the shelf closed; use Day on first launch. Remember only the destination across restarts, not its selected date, scroll position, or draft. Existing feature preferences retain their own persistence rules.
- The shelf pushes the full-width current page right, leaving a visible strip. A left swipe on the shelf, tapping that strip, or Android Back closes it. Selecting a destination opens that screen and closes the shelf.
- Main destination headers use a sidebar-panel icon to open the shelf. Existing horizontal page gestures retain their meaning; there is no swipe-right opening gesture.
- Keep the current grouped directory, all destinations under fixed headings, and a selected-state indication for the current screen.
- Opening and closing the shelf keeps the current page mounted, preserving dates, filters, scroll position, and unfinished input. Switching destinations resumes their current-session state.
- The optional Shortcut Bar is hidden while the shelf is open. Its other visibility rules and preferences remain unchanged.
- Hide global navigation during editing, while the keyboard is open, in Focus Mode, during Day Planning, and on detail screens. Preserve their existing exit controls; leaving Focus Mode does not stop Activity Tracking.
- Details and contextual links return to their source and preserved state. Targeted links override the destination selection as needed.
- Switching destinations does not accumulate top-level history. With the shelf closed, Back from a main destination uses normal Android exit behavior; contextual and detail returns take precedence.

CONTEXT.md defines Navigation Shelf; [design notes](../navigation-shelf-design.md) record the confirmed interview decisions.

## Quick access revision (2026-10-25; Navigate removed 2026-10-26)

The Shortcut Bar uses icons above labels and a full selected-item background, grouped in one rounded tonal surface. It floats over the app with transparent space around and below it. Scrolling content continues behind it and includes enough trailing clearance to bring the last item above it; fixed actions stay above it. Navigate and Add shortcut were removed on 2026-10-26; empty bars are hidden.

- Day and Battle Plan are the initial shortcuts. Any directory destination can be selected, including Settings; users can remove all shortcuts.
- Edit shortcuts is accessible through the Navigation Shelf's Preferences group. It supports all directory destinations without a three-item limit, removing and reordering them, and an editable shortcut bar with long-press drag reordering, edge scrolling, visible remove buttons, and accessible move/remove actions. Save, Cancel, and Back return to the surface that opened the editor.
- Save commits the ordered list locally on this Android installation. Cancel and Back discard draft changes. An empty saved list remains empty across restarts. Preferences do not synchronize between devices.
- Shortcuts use the same navigation path as directory selection, including preserved destination state and Back behavior. Selecting the current shortcut does nothing. Day resumes its selected date rather than jumping to Today.
- The entire dock follows the existing visibility rules. The editor hides the originating surface and returns there after Save or Cancel.

The throwaway comparison is preserved on branch `codex/navigation-shortcuts-prototype`, commit `a6218e3d`. The implementation excludes the comparison controls and prototype routes.
