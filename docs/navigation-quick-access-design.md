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
