# Timebox for Android

Native Kotlin + Jetpack Compose client for the Timebox API, built from the
`Timebox Android.dc.html` design. It is a separate surface from the web frontend:
both talk to the same FastAPI backend, and neither shares code with the other.

## What is here

| Screen | Notes |
| --- | --- |
| **Day** | Dual-lane planned/actual timeline on the 30-minute grid, hour gutter, now line, tap-to-create, long-press then drag to move, grooves to resize, bottom sheet to edit. |
| **Battle Plan** | Projects and admin tasks, subtasks, filters, deadlines, reminders, Ready to Plan, archive/trash, and task detail editing. |
| **Recurring** | Scheduled and quota-based templates with preview, create/edit, pause, resume, end, and delete flows. |
| **Chronicle** | Month grid; days that have at least one block show their window (e.g. `8–20`). Any cell opens that date in Day. |
| **Types** | Slash-path task types grouped by root, with usage counts and delete. |
| **Settings** | Day-window steppers, full-24h toggle, dark theme, and the server address / API key for this device. |

The earlier Day Review screen has been removed. Reporting is expected to be redesigned,
and the old screen may not return; the read-only day-summary endpoint remains available
as a possible reporting primitive.

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

Once the wrapper exists you can also build from PowerShell at the repository root. The launcher discovers a compatible JDK (including Android Studio's bundled JBR) and selects it only for the Gradle process:

```powershell
.\scripts\android-gradle.ps1 assembleDebug
```

On macOS or Linux, set `JAVA_HOME` to JDK 17+ and run `./gradlew assembleDebug` from this directory.

## Dark-theme visual regression screenshots

With an emulator connected, regenerate the deterministic Task Details, menu,
dialog, Day calendar, and Chronicle dark-theme screenshots from the repository
root:

```powershell
.\scripts\android-dark-theme-screenshots.ps1
```

The script runs only `DarkThemeScreenshotTest` and pulls its PNGs into
`artifacts/android-dark-theme/` for side-by-side review. The deterministic set also
covers grouped editors, collapsed empty Plan Mode, and both themes of the component
gallery.

## Pointing the app at your API

Debug builds default to `http://10.0.2.2:8001/`, which is the emulator's alias for
the host machine — run the backend on the registered Timebox API port `8001` and it
connects with no setup.

For a physical device or another host, open **Settings → Server** in the app and set
the address (for example `http://192.168.1.20:8001`). The value is stored in
DataStore per device.

Cleartext HTTP is permitted only for `localhost`, `127.0.0.1`, `10.0.2.2` and
`192.168.*` — see `res/xml/network_security_config.xml`. Anything else must be
HTTPS.

## API key

The backend leaves the API open unless `API_KEY` is set. When you do set it, every
application request—including days, settings, task types, projects, tasks, reminders,
and recurring templates—must carry a matching `X-API-Key` header; `/health` stays open.

```bash
API_KEY=some-long-random-string
```

Put the same value in **Settings → Server → API key** in the app. Without a key the
API returns 401; with a wrong key, 403. The web frontend does not send the header,
so setting `API_KEY` will lock it out — leave it unset while you are relying on the
browser UI.

## Backend endpoints this client adds

- `GET /days/{date}/preview` — renderable blocks and window settings for the live
  adjacent-page swipe. Missing dates stay read-only, so peeking and snapping back does
  not add an empty Chronicle row.
- `GET /days/{date}/summary` — planned/actual totals plus per-task-type minutes for
  future reporting. Read-only: unlike `GET /days/{date}` it does not create the day.
- `GET /task-types` now includes `usage_count` per row, for the Types screen.
- `GET /days` now includes `block_count` per row. Opening a date creates the day, so the
  archive fills with empty entries; the count is what lets Chronicle print a window label
  only for days that actually hold something.
- `/projects`, `/tasks`, and `/reminders` provide the Battle Plan, Ready to Plan, and
  notification workflows.
- `/recurring-templates` provides preview and lifecycle operations for scheduled and
  quota-based recurring work.

## Typography

The design's Manrope headlines and Inter body text are bundled as the official Google
Fonts variable builds under `app/src/main/res/font`. Their SIL Open Font License texts
are kept in `app/src/main/res/raw`. Weight resolution lives in
[`ui/theme/Type.kt`](app/src/main/java/com/timebox/android/ui/theme/Type.kt).

The in-app component gallery is available at **Settings → Appearance → Theme preview**.
It provides a data-free reference for the surface ladder, typography, fields, chips,
primary and disabled actions, and compact empty states in either theme.

Icons come from `material-icons-extended` rather than the design's Material Symbols
webfont; the glyph names map one-to-one.

## Known behaviours worth knowing

- **Movement is armed by a long press.** Touch and stylus users hold a movable
  Planned Block, Planning Draft, or Tasks-to-Plan card until the haptic, then drag
  to move it. Existing Planned and Actual Block resize grooves drag immediately.
  Existing Block movement and resizing apply five-minute deltas without normalizing
  saved times. New Block placement uses the nearest five-minute mark, while the
  timeline grid and minimum Planned Block duration remain 30 minutes.
  Movement before the haptic remains available to timeline scrolling and day
  swiping; mouse dragging remains immediate.
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
- **Online only.** The Day screen keeps only its current and adjacent pages in memory
  for swiping; there is no persistent/offline cache. Reads show a retry on failure.
- **Reminder delivery needs notification permission.** The app checks due reminders in
  the background and acknowledges notifications after successful delivery.
- **Overlaps are rejected by the server.** Dragging a block onto another in the same
  lane returns 422 and the timeline snaps back with a message.
