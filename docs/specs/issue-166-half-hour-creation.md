# Issue #166: half-hour defaults for manual Block creation

Source: https://github.com/crimsoncaius/timebox/issues/166

Status: design confirmed and implementation requested on 2026-09-14.

## Confirmed decisions

- Apply manual empty-slot click/tap creation defaults to both web and Android,
  for both Planned Blocks and Actual Blocks.
- Round the clicked time down to the containing half-hour. A click at 16:20
  requests 16:00–16:30; a click at 16:30 requests 16:30–17:00.
- The initial requested duration is 30 minutes.
- Subsequent time editing remains precise, including 16:05. Saving and
  reopening must preserve those adjusted times without half-hour rounding.
- Half-hour alignment defines the requested start, not a restriction on the
  resolved placement. Preserve issue #141's nearest-fitting-space policy,
  full 30-minute duration, and later-start tie break.
- Placement may resolve to exact-minute boundaries: for example, a 16:00
  request may become 16:05–16:35 when an existing Block ends at 16:05.
- Actual Blocks remain restricted to elapsed time. At 16:20, a requested
  16:00–16:30 may resolve to 15:50–16:20, subject to available space.
- Search within the existing allowed day range; reject only when no complete
  30-minute placement fits. Do not shorten the Block to force a fit.
- Apply the same defaults to clicks/taps with a Ready to Plan task selected,
  including occupied-time clicks that resolve to nearby available space.
- Preserve existing finer movement and resizing after initial placement,
  including before saving, and exact-minute time fields for saved Blocks.
- Keep drag-to-create behavior unchanged. No new editor is required.

## Acceptance examples

- With available space, clicking/tapping 16:20 requests and selects
  16:00–16:30; 16:30 selects 16:30–17:00.
- The same rule applies on web and Android, in Planned and Actual lanes,
  subject to the Actual elapsed-time limit.
- A selected Ready to Plan task uses the same 30-minute request.
- When the requested range is unavailable, resolve using issue #141 rather
  than requiring the resulting start to remain on the half-hour grid.
- On a full-day timeline with available space, 23:59 selects 23:30–24:00,
  subject to the Actual elapsed-time limit.
- After placement, a user can adjust a Block to start at 16:05. Saving and
  reopening preserves the adjusted start and end without half-hour rounding.

## Scope and existing behavior

Web already floors ordinary empty-slot clicks to half-hour rows; Android
currently rounds those taps to five-minute increments. Both initialize
30-minute drafts and use nearest-space resolution. Selected-task clicks/taps
also currently use 30 minutes. Apply the agreed rules consistently without
changing Activity Tracking, recurring placement, drag-to-create, or the
existing finer editing increments.

These defaults introduce no new domain term or architectural decision requiring
a glossary entry or ADR.

## Implementation and verification

Android's ordinary tap and selected-task tap handlers now use a dedicated
half-hour floor operation. Five-minute drag snapping, placement resolution,
duration, and persistence remain unchanged. Web already used the agreed
mapping; component tests now explicitly cover both lanes and task selection.

- Web: 45 focused component/time/placement tests passed; production build and
  changed-file lint passed. Browser checks created 16:00–16:30 Blocks from
  16:20 clicks in both lanes and confirmed 16:05–16:35 edits survive reload.
- Android: 15 focused unit tests passed, including initial mapping, day-end
  bounds, finer interactions, drag placement, and pending Actual moves.
  Application and instrumentation APK builds passed. Device checks confirmed
  both-lane tap mapping, selected-task placement over occupied time, and
  five-minute Actual resizing (three passing device checks). Fresh-emulator
  system-service ANRs interrupted the first resize retry; the next run passed.
- A broader legacy Work Mode unit run failed restoration and coroutine-lifetime
  checks; its Actual-resize persistence test also failed. Those tests are
  unchanged and the broader suite is not claimed green.
- The existing Actual-resize gesture fixture now places its server clock after
  the resize destination, respecting the elapsed-time restriction.

The user explicitly requested another emulator while the ordinary pool was full.
The helper now supports `acquire --extra` for that explicit request, with a
reservation test covering the ordinary capacity limit, preservation of active
and review reservations, reuse, and token invalidation.

Review servers use API port 12020 and web port 12021, reserved for this worktree.
The API uses the isolated `backend/issue166.sqlite.local` database with
`ACTIVITY_TRACKING_DEV=true`, `AUTO_CREATE_TABLES=1`, and
`APP_TIMEZONE=Asia/Singapore`. The Android review build uses
`-PreviewApiBaseUrl=http://10.0.2.2:12020/`.

Android review uses `emulator-5584`, reservation
`2a2b0b3dca0c4dae9df542324695f224`. The user approved the result and requested
merging and releasing the emulator on 2026-09-14; the reservation is released.
