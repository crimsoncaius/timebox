# Android navigation quick access

Status: prototype C selected by the user on 2026-10-25 and implemented; awaiting user review before merge. Tracked in [issue 320](https://github.com/crimsoncaius/timebox/issues/320).

Later revisions: [Home navigation](design/home-navigation.md) supersedes the Navigate/Add shortcut controls below and replaces the editor's duplicate ordered list and passive preview with an editable shortcut bar. The sections below retain the earlier design and validation history.

## Accepted design

The inset dock visually separates two roles: horizontally scrollable shortcuts share a rounded tonal surface, and Navigate has its own matching rounded surface at the right, separated by a 10 dp gap. Add shortcut follows the saved shortcuts and remains available for an empty selection. Icons sit above text; the current destination gets a filled rounded background. Navigate stays fixed while shortcuts scroll; switching destinations or returning from editing or the directory resets the strip to its beginning. The subsequent floating-navigation refinement overlays these surfaces on the screen, with transparent surrounding space and scroll clearance below content.

- Start with Day and Battle Plan. Any directory destination is eligible; all shortcuts may be removed.
- Navigate always opens the complete directory. Edit shortcuts is also available directly through Add shortcut and supports selecting, removing, and reordering all directory destinations, with a scrollable preview.
- Save commits the ordered selection on this Android installation. Cancel and Back discard drafts. Empty selections persist; there is no three-shortcut limit.
- Shortcuts preserve the existing navigation, destination state, Back, and visibility behavior. Day is distinct from Today. Actions such as creating tasks or starting Activity Tracking are outside scope.
- Feedback and undo placement account for the dock's measured height.

The revised decision is recorded in [ADR 0020](adr/0020-full-page-mobile-navigation.md).

## Prototype source

Question settled: which bar structure should provide direct access while retaining Navigate?

Verdict: the user selected C, the inset dock, after comparing A (equal tabs), B (split controls), C (inset dock), D (translucent overlay), and E (individual floating pills inspired by their Notion screenshots).

Primary source: local branch `codex/navigation-shortcuts-prototype`, commit `a6218e3d`. That branch contains the runnable comparison, launcher, and full exploration notes. Production code excludes prototype routes and controls.

## Review

Implementation branch: `codex/navigation-shortcuts`. Merge requires the user's explicit instruction; no pull request was requested.

Managed review device: `emulator-5594`, token `43683640e3424ed0ac4bfef0b81de463`, storage root `C:\Users\Caius\TimeboxRuntime\emulators`.

Validation: the debug build passed. Twelve targeted instrumentation cases passed across the initial run and one isolated retry: the task-detail Back case initially lost Espresso window focus and passed unchanged on retry. Coverage includes persistence and explicit empty selections, order, capacity, configuration restoration of editor drafts, Cancel/Back, save-error retry, current-item no-op, full directory access, keyboard visibility, conversation drafts, Calendar state, and detail-screen Back behavior.

The installed app also retained an added Habits shortcut after force-stop and restart. The default Day/Battle Plan selection was restored afterward. The editor and live dock preview are left open for review. The local API was subsequently started on port 8001, with its database backed up and migrated; the app now loads live Day data.

## Shortcut motion decision (2026-10-25)

After comparing the native motion prototype, the user selected the full-width
slide at 250 ms. Tapping a shortcut to the right moves the page left; tapping
one to the left reverses it. Headings move with their destination and the dock
stays fixed. Direction follows the saved shortcut order. Reselecting the current
shortcut does nothing; unpinned sources and contextual
navigation keep their previous transition. Android Remove animations skips the
slide. This is tap-triggered motion, not a finger-swipe navigation gesture.

Implementation is isolated on `codex/navigation-motion`. The original comparison
is preserved on `codex/shortcut-motion-prototype` at `ac467cf0`.

## Directory motion decision (2026-10-25)

The user selected A, Rise and reveal, at 250 ms for opening Navigate, closing it,
and choosing a destination. Navigate rises from the bottom over the source screen.
Close and Android Back lower it to reveal the unchanged source; selection lowers
it to reveal the chosen destination already in place, without an additional page
slide or crossfade. Choosing the current destination simply closes the menu.
Input and accessibility access to the covered screen resume after the menu exits.
Android Remove animations makes both transitions immediate.

The menu comparison is preserved on `codex/navigation-menu-motion-prototype`,
commit `46d336c7`. Implementation remains on `codex/navigation-motion`, separate
from the debug-only comparison controls and illustrative pages. Existing directory
state restoration and shortcut-editor behavior remain in effect.

Validation: debug build and six focused instrumentation cases passed together,
including measured upward entry/downward dismissal for Close and selection,
covered-screen accessibility, shortcut motion, conversation drafts, Calendar
state, and Back. An initial Calendar case reached an unmocked Trends response
and crashed; it passed in isolation and on the full focused rerun without a
production workaround. The cause of that first-run failure is unconfirmed.
The installed app was also checked for Close, destination selection, and returning
from Edit shortcuts. Navigate is left open on emulator-5594 for review.

## Compact island revision — 2026-10-26

The user selected A from the single-shortcut comparison. The dock now centers and wraps its contents instead of filling the screen. One saved shortcut plus Add forms a small island; an empty list still shows Add. The island grows up to four visible items, then scrolls with the existing item snapping. Four saved destinations are visible together; Add follows them offscreen. This is a visible-slot limit, not a saved-shortcut limit.

Items retain an 88 dp preferred width and share available space equally when four would otherwise exceed the viewport. The live dock and editor preview use the same sizing. Navigation, selected states, scroll resets, local persistence, and content clearance remain unchanged.

Primary source: `codex/single-shortcut-prototype`, commit `c2f30301`. Implementation: `codex/compact-shortcut-island`. The comparison activity and controls remain on the throwaway branch.

Validation: all 9 NavigationShortcutsTest instrumentation cases passed on emulator-5596, including the new constrained-width layout check. Installed and launched the actual app; visually checked one and four saved shortcuts. Review is left on Day with Day, Battle Plan, Calendar, and Trends visible. Review token: 060a7ac025f94217814009638e94c2f1. Storage root: C:/Users/Caius/TimeboxRuntime/emulators.
