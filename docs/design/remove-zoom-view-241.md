# Remove standalone Zoom view — #241

The Day surface no longer has a standalone Zoom bar or a Zoom visibility switch on Android or web. View already exposes the current scale and Reset zoom, making the extra display redundant.

The user accepted both-platform scope and keeping web keyboard adjustment in View. Web View now includes a labelled zoom slider; arrow keys retain multiplicative adjustment within 0.5×–12×. Reset returns to 1.0×. Timeline gestures and coordinate scaling are unchanged.

Calendar and Activity Tracking visibility remain independent and device-local. Legacy Zoom visibility values are ignored, preserving the remaining preferences. This supersedes the Zoom presentation in [Day visibility](day-visibility-172.md) and [short blocks](short-blocks-170-167.md).

This is a reversible presentation change with no new domain terminology or architectural decision, so no glossary entry or ADR is needed.

## Verification

- Eight focused web tests pass: preferences, obsolete saved fields, keyboard zoom limits, reset/gesture continuity, and short-block geometry.
- Web production build and ESLint for affected files pass.
- Live browser verification confirms keyboard adjustment/reset and preference compatibility, including a narrow viewport.
- All 311 Android unit tests and the debug build pass. Both focused device tests (preference persistence and pinch zoom) pass. The installed app and View dialog were visually verified.

## Review environment

Changes remain on `codex/241-remove-zoom-view` pending user review and explicit merge instruction.

- Web: http://127.0.0.1:12043/day/2026-09-21
- Isolated review API: http://127.0.0.1:12042, using `.out-of-scope/zoom-review.sqlite`.
- Android: `emulator-5610`, retained for user review with View open. Reservation token: `fb12464e3a5a46bdb23168e48fba5fb3`.
- Android review build uses `-PreviewApiBaseUrl=http://10.0.2.2:12042/`.

