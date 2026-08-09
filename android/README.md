# Timebox for Android

Native Kotlin + Jetpack Compose client for the Timebox API, built from the
`Timebox Android.dc.html` design. It is a separate surface from the web frontend:
both talk to the same FastAPI backend, and neither shares code with the other.

## What is here

| Screen | Notes |
| --- | --- |
| **Day** | Dual-lane planned/actual timeline on the 30-minute grid, hour gutter, now line, tap-to-create, drag to move, grooves to resize, bottom sheet to edit. |
| **Chronicle** | Month grid; days that have at least one block show their window (e.g. `8–20`). Any cell opens that date in Day. |
| **Types** | Slash-path task types grouped by root, with usage counts and delete. |
| **Settings** | Day-window steppers, full-24h toggle, dark theme, and the server address / API key for this device. |
| **Review** | Planned vs actual totals, per-root comparison bars, and drift copy. Reached from the chart icon on the Day top bar. |

Deliberately **not** built (out of scope for this pass): the onboarding carousel,
the quick-log FAB timer, and the lock-screen glance widget.

## Prerequisites

- Android Studio (Ladybug or newer) with the Android SDK
- JDK 17+ — Android Studio's bundled JBR works
- A running Timebox backend (see the root [README](../README.md))

## First build

The Gradle wrapper JAR is not committed, so use Android Studio for the first sync:

1. **File → Open** and select the `android/` directory.
2. Studio will offer to generate the Gradle wrapper and download the distribution
   pinned in `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.11.1). Accept.
3. Let it create `local.properties` pointing at your SDK, then **Sync Now**.

The build targets `compileSdk`/`targetSdk` 34 and `minSdk` 26.

Once the wrapper exists you can also build from the terminal:

```bash
cd android && ./gradlew assembleDebug
```

## Pointing the app at your API

Debug builds default to `http://10.0.2.2:8000/`, which is the emulator's alias for
the host machine — run the backend on port 8000 and it connects with no setup.

For a physical device or another host, open **Settings → Server** in the app and set
the address (for example `http://192.168.1.20:8000`). The value is stored in
DataStore per device.

Cleartext HTTP is permitted only for `localhost`, `127.0.0.1`, `10.0.2.2` and
`192.168.*` — see `res/xml/network_security_config.xml`. Anything else must be
HTTPS.

## API key

The backend leaves the API open unless `API_KEY` is set. When you do set it, every
`/days`, `/settings` and `/task-types` request must carry a matching `X-API-Key`
header; `/health` stays open.

```bash
API_KEY=some-long-random-string
```

Put the same value in **Settings → Server → API key** in the app. Without a key the
API returns 401; with a wrong key, 403. The web frontend does not send the header,
so setting `API_KEY` will lock it out — leave it unset while you are relying on the
browser UI.

## Backend endpoints this client adds

- `GET /days/{date}/summary` — planned/actual totals plus per-task-type minutes for
  the Review screen. Read-only: unlike `GET /days/{date}` it does not create the day,
  so opening Review never adds a date to the archive.
- `GET /task-types` now includes `usage_count` per row, for the Types screen.
- `GET /days` now includes `block_count` per row. Opening a date creates the day, so the
  archive fills with empty entries; the count is what lets Chronicle print a window label
  only for days that actually hold something.

## Typography

The design specifies Manrope for headlines and Inter for body text. Neither is
bundled — that would mean committing binary font files — so both fall back to the
platform sans face while keeping every weight, size and tracking value from the
design. To switch on the real faces, drop the TTFs into `app/src/main/res/font` and
replace `HeadlineFamily` / `BodyFamily` in
[`ui/theme/Type.kt`](app/src/main/java/com/timebox/android/ui/theme/Type.kt) with
`FontFamily(Font(R.font.…, FontWeight.…), …)`. Nothing else needs to change.

Icons come from `material-icons-extended` rather than the design's Material Symbols
webfont; the glyph names map one-to-one.

## Known behaviours worth knowing

- **Blocks capture vertical drags.** As in the design (`touch-action: none` on
  blocks), starting a drag on a block moves it rather than scrolling the timeline.
  Scroll from the gutter or an empty part of a lane.
- **Notes save on dismiss.** Choosing a task type and dragging save immediately; the
  note field writes when the sheet closes, to avoid a request per keystroke.
- **The type picker can create types.** Typing a path that does not exist offers a
  create row; committing it writes the leaf plus any missing ancestors server-side and
  assigns it in the same gesture, so a new type never means leaving the sheet. Ranking
  and canonicalization live in
  [`data/TaskTypePaths.kt`](app/src/main/java/com/timebox/android/data/TaskTypePaths.kt)
  and are unit-tested; note they deliberately rank differently from the web frontend's
  `taskTypePaths.ts`, which scores by segment alignment.
- **The picker's field autofocuses only for new blocks.** Editing an existing block
  keeps the keyboard down so Mark complete and delete stay reachable.
- **Online only.** Nothing is cached. Every screen reads through to the API and shows
  a retry on failure.
- **Overlaps are rejected by the server.** Dragging a block onto another in the same
  lane returns 422 and the timeline snaps back with a message.
