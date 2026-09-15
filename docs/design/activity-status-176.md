# Activity Tracking status appearance — issue 176

## Context

Issue 176 is an aesthetic pass on the compact Activity Tracking status (Offline / Unsynced / Synced, Saving…, Connection required, Retry). Both web and Android.

## Settled direction (implemented)

Attention-only **chip** (Zoom-like hairline pill) on Day and Focus, both platforms.

- **Healthy Synced** draws nothing. The condition still exists; the chrome is empty. This reopens #148’s “Synced remains visible” for this chrome only.
- **Busy** is one compact status: Saving… replaces Synced/Unsynced; Offline still prefixes (`Offline · Saving…`).
- **Retry** is inside the chip (`Unsynced | Retry`). Generic “Change not confirmed.” is dropped.
- **Specific failure copy** is hidden. Storage or connection failure looks like Unsynced.
- **Day web**: chip on the left; Start tracking / Focus (and running Switch/Stop) stay on the right so they do not shift.
- **Focus web**: chip stacks under the tracking controls, still secondary.
- **Android**: same chip under Current activity / Focus identity in the tracking column.

## Coverage deferred

Hidden tracking shortcut still uses the current “Tracking !” mark and does not show the chip.
