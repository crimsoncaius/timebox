---
status: accepted
---

# Full-page mobile navigation

The navigation redesign replaces peer bottom tabs with a full-page directory, making the application's destinations directly discoverable without forcing them under unrelated parent surfaces. Home is the launch destination and directory, with destinations under non-interactive group headings. The original single-button, two-tap choice was revised on 2026-10-25 to add optional one-tap destination shortcuts in the inset dock described below. The directory itself still lists each destination once.

Home replaces the Navigate overlay as of 2026-10-26. Day remains distinct from Chronicle.

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

Remove local sibling tabs duplicated by the directory. Keep destination-specific controls such as month navigation, date ranges, and task filters. Keep useful contextual links, including access to Recurring Tasks from Battle Plan; move the dedicated Task Types management entry out of Battle Plan to Home.

## State, visibility, and Back (revised 2026-10-26)

- A fresh launch starts on Home. Home is the existing directory titled Home, with no Close control.
- The floating dock retains only its scrollable user-selected shortcuts. Remove the separate Navigate and Add shortcut items; hide the dock when empty. The strip resets on destination changes and returns from the shortcut editor.
- Selecting a destination resumes its current-session state, including selected date, filters, scroll position, and drafts. Full restoration after process death is outside this agreement.
- Main destination headers have an arrow immediately left of the title. Direct destinations return to preserved Home; Home has no back arrow.
- Hide global navigation during editing, while the keyboard is open, in Focus Mode, and on detail screens. Existing editor, detail, and Focus Mode exit controls remain; leaving Focus Mode does not stop Activity Tracking.
- Details and contextual links return to their source and preserved state. Targeted links override the destination selection as needed.
- Switching destinations does not accumulate top-level history. Android Back from a direct destination returns Home while preserving its state; Back from Home uses normal Android exit behavior. Contextual and detail returns take precedence.

CONTEXT.md records the platform-specific surface hierarchy without changing the underlying domain concepts.

## Quick access revision (2026-10-25; Navigate removed 2026-10-26)

The following records the original dock treatment; the Home revision above removes both Navigate and Add shortcut. Empty docks are now hidden. The user selected prototype C after comparing five treatments. The dock uses icons above labels and a full selected-item background. A subsequent user-requested refinement groups shortcuts in one rounded tonal surface and places Navigate in its own adjacent rounded surface, making their different roles visible. When all shortcuts are removed, Add shortcut remains available beside Navigate. A subsequent user-requested refinement makes these surfaces float over the app, with transparent space around and below them. Scrolling content continues behind the dock and includes enough trailing clearance to bring the last item above it; fixed actions stay above the dock. This replaces the original choice to reserve layout space beneath the screen.

- Day and Battle Plan are the initial shortcuts. Any directory destination can be selected, including Settings; users can remove all shortcuts.
- Edit shortcuts is accessible through Home's Preferences group. It supports all directory destinations without a three-item limit, removing and reordering them, and an editable shortcut bar with long-press drag reordering, edge scrolling, visible remove buttons, and accessible move/remove actions. Save, Cancel, and Back return to the surface that opened the editor.
- Save commits the ordered list locally on this Android installation. Cancel and Back discard draft changes. An empty saved list remains empty across restarts. Preferences do not synchronize between devices.
- Shortcuts use the same navigation path as directory selection, including preserved destination state and Back behavior. Selecting the current shortcut does nothing. Day resumes its selected date rather than jumping to Today.
- The entire dock follows the existing visibility rules. The editor hides the originating surface and returns there after Save or Cancel.

The throwaway comparison is preserved on branch `codex/navigation-shortcuts-prototype`, commit `a6218e3d`. The implementation excludes the comparison controls and prototype routes.
