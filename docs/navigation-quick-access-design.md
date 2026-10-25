# Android navigation quick access

Status: behavior agreed; throwaway UI prototype available for comparison. No visual treatment has been selected for production.

## Confirmed direction

- Quick access provides shortcuts to destination screens.
- Navigate continues to open the full navigation directory.
- Creating tasks and starting Activity Tracking are outside this mechanism.
- Users choose their shortcuts, starting from sensible defaults.
- Shortcut positions remain stable rather than changing with recent usage.
- Navigate remains permanently available within the navigation bar.
- The bar supports up to three destination shortcuts plus Navigate, in one row with icons and text labels.
- Day and Battle Plan are pinned initially. The third slot is left for the user to choose; it is not filled automatically.
- An Edit shortcuts control inside the Navigate directory lets users select, remove, and reorder shortcuts.
- Navigate stays at the right end. An unused shortcut slot has no permanent add button.
- Shortcuts use existing destination navigation: resume previous screen state, highlight the selected destination, and do nothing when that destination is tapped again.
- Existing Back behavior and bar visibility rules remain: hide during editing, keyboard use, detail screens, and Focus Mode.
- Shortcut selections and ordering persist across app restarts.
- Any directory destination can be pinned. Users may remove every shortcut; Navigate then remains on the right by itself.
- Preferences are local to this Android installation, without account synchronization.
- The editor uses Save and Cancel. Back discards unsaved edits.
- At capacity, show "3 of 3 selected" and require removing a shortcut before adding another. Removal and reordering remain available; no shortcut is automatically replaced.

## Existing decision to revisit

[ADR 0020](adr/0020-full-page-mobile-navigation.md) chose a single bottom Navigate button and accepted two taps between destinations. Destination shortcuts would revise that choice. The ADR remains unchanged while the replacement design is being discussed.

## UI prototype

Question: which bar structure makes pinned screens easy to reach while keeping Navigate distinct?

Branch: `codex/navigation-shortcuts-prototype`.

- A — Equal tabs: icon above label, with a compact selected icon background.
- B — Split controls: icons beside labels, with Navigate separated by a rule.
- C — Inset dock: grouped tonal surface with a full selected-item background.

The variants run on the actual Android destination screens. The native equivalent of the UI skill's URL switcher uses `timebox://prototype/navigation-shortcuts?variant=A` (also B and C), with a debug-only comparison bar below the proposed navigation. It updates the Activity's deep-link URI. Preferences remain in memory for this throwaway experiment; the agreed production behavior still requires local persistence.

Build with `./scripts/android-gradle.ps1 assembleDebug`, install the resulting APK through the managed emulator helper, then launch with `./scripts/navigation-shortcuts-prototype.ps1 -Token <reservation-token> -Variant A`. The launcher defaults to this chat's review reservation.

Try Navigate → Edit shortcuts, add a third destination, reorder it, and Save. Compare all three variants with the same selections. Also try Cancel, Back, the three-item limit, and removing all shortcuts.

## Pending review

- Validate label fit, touch targets, selected states, unused space, and the editing interaction in the app.
- Review the concrete UI before implementation is considered agreed.

The prototype is gated to debug deep-link launches. No production shortcut behavior has been implemented.

## Review evidence

- Debug APK build succeeded. Manual emulator checks covered A/B/C switching, adding a third shortcut, rejecting a fourth, reordering, saving, cancelling, and saving no shortcuts. The editor no longer exposes the directory behind it to accessibility.
- Screenshots: `artifacts/navigation-review/shortcuts-a.png`, `shortcuts-b.png`, `shortcuts-c.png`, `shortcuts-editor.png`, and `shortcuts-final.png` (local review artifacts).
- Pending review: emulator `emulator-5594`, reservation `43683640e3424ed0ac4bfef0b81de463`, storage root `C:\Users\Caius\TimeboxRuntime\emulators`.
- Left running on A with Day and Battle Plan. Agent recommendation: A for label space and the least visual weight; user verdict pending.
- Production persistence and broader navigation regression tests are deferred until a treatment is selected. Preserve this throwaway branch as the primary source and link it from the implementation issue when that work is scoped.
