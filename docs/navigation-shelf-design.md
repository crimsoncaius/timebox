# Navigation shelf design

Status: implemented and validated on branch `codex/navigation-shelf`; awaiting user review.

## Confirmed decisions

- Keep Timebox's visual style; use the Obsidian references for sliding sidebar behavior.
- Opening the shelf preserves the current page, including scroll position and unfinished input. Swiping left closes the shelf and reveals that page.
- Launch on the last-used page with the shelf closed.
- Scope is Android only. Navigation Shelf replaces the full-page Home concept.
- Swipe left on the open shelf to reveal the current screen. Selecting a destination opens that screen and closes the shelf.
- Replace the top-left arrow on main destinations with the sidebar-panel icon shown in the Obsidian reference; tapping it opens the shelf. Existing page-content swipe gestures remain unchanged. No swipe-right opening gesture is requested.
- Opening the shelf pushes the page right, leaving a narrow strip visible. Tapping that strip closes the shelf.
- Keep existing destination groups as fixed headings with all destinations visible; highlight the active destination.
- Keep the optional Shortcut Bar when the shelf is closed, subject to its existing visibility rules; hide it while the shelf is open.
- Remember the last main destination for launch, with Day as the first-launch fallback. Editors and details are not startup destinations.
- Android Back closes the open shelf. With the shelf closed, contextual and detail navigation returns to its source; Back from a main destination uses normal Android exit behavior. Switching main destinations does not accumulate Back history.
- Preserve existing exit controls and global-navigation restrictions in editors, detail screens, Focus Mode, and active planning. Replace only main-screen navigation arrows with the sidebar icon.
- Across application restarts, remember only the last main destination. Dates, filters, scroll positions, and unfinished drafts are not restored as part of this change; Day starts on Today. During use, opening and closing the shelf preserves the current page's state.

## Existing decision affected

[ADR-0020](adr/0020-full-page-mobile-navigation.md) specifies a full-page Home and launch on Home. The confirmed direction revises those choices; the remaining navigation rules are still under discussion.

## Completion

The user confirmed the complete design and authorized implementation and a running Android review. ADR-0020 now reflects the replacement; unrelated shortcut and contextual-navigation decisions remain in force.

## Validation and review

- Debug APK built successfully.
- All 35 focused instrumentation cases passed across the initial run and a focused rerun. The initial run had nine window-focus failures caused by a Pixel Launcher ANR dialog; after dismissing it, all nine passed without code changes.
- All seven `AppRoutesTest` unit cases passed.
- Device verification: shelf pushes the active page aside, the sidebar icon matches the reference, a cold restart restores Battle Plan with the shelf closed, and Back from a main destination returns to Android's launcher.
- Pending review: `emulator-5594` (`timebox-agent-08`), token `0936c1f1cdc14c25a90b002d6742fd13`. Shelf left open beside Battle Plan.
- Device storage: `C:/Users/Caius/TimeboxRuntime/emulators/avds/timebox-agent-08.avd`.
- Isolated review backend: `http://127.0.0.1:8001`, with a fresh SQLite database at `artifacts/navigation-shelf/review.sqlite`. This review does not use the user's normal database.
- Screenshot and local test evidence: `artifacts/navigation-shelf/` (ignored). Leave the emulator and backend available until review is complete.

## Aesthetic refinement — 2026-10-29

Implemented the HTML review's proposed direction: open groups separated by whitespace, the existing 27sp screen-title scale with 12dp vertical padding, plain Current text within one selected-row background, and a one-pixel page-edge hairline while the shelf is visible. Group borders and row dividers are removed, including the inconsistent divider above selected Settings. Existing icons, labels, 52dp minimum destination targets, and navigation behavior remain.

Shelf background refinement: light mode uses surf (#EBEEEF); dark mode uses low (#1C1917), including the heading and system-bar inset areas. The current page keeps bg, selected rows keep high, and the page-edge hairline remains.

Selected-row follow-up: light mode uses highest (#DDE4E5) for a stronger distinction from the shelf; dark mode retains high. Rebuilt and visually checked on the review device.

Selection label follow-up: removed the redundant Current text. The selected-row background and selected accessibility semantics identify the active destination.
