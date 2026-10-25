# Android navigation quick access

Status: prototype C selected by the user on 2026-10-25 and implemented; awaiting user review before merge. Tracked in [issue 320](https://github.com/crimsoncaius/timebox/issues/320).

## Accepted design

The inset dock visually separates two roles: up to three shortcuts share a rounded tonal surface, and Navigate has its own matching rounded surface at the right, separated by a 10 dp gap. An empty shortcut group is not drawn. Icons sit above text; the current destination gets a filled rounded background. The dock occupies layout space and retains unused shortcut slots, matching prototype C.

- Start with Day and Battle Plan. Any directory destination is eligible; all shortcuts may be removed.
- Navigate always opens the complete directory. Edit shortcuts inside it supports selecting, removing, and reordering up to three destinations, with a live preview.
- Save commits the ordered selection on this Android installation. Cancel and Back discard drafts. Empty selections persist; nothing is automatically replaced at capacity.
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
