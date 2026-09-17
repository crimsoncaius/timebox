# Day view visibility — implementation

## Current implementation status

The user approved implementation after the design rounds below. This section supersedes historical pending decisions and launch state in those rounds. The approved prototype is preserved at commit `4fb4746` on `codex/issue-172-approved-prototype`; production work is on `codex/issue-172-implementation`.

- Android and web provide independent Calendar, Activity Tracking, and Zoom switches in the approved Day view dialog. First-use defaults are Calendar shown, Tracking hidden, Zoom hidden.
- Android DataStore and web localStorage retain choices locally across visits and restarts. No backend preference sync was added. Web storage failures retain the current visit's selection and explain that it could not be saved.
- Hiding tracking keeps its lifecycle active. The compact Tracking shortcut restores controls; check-ins and recovery remain reachable. Hiding Zoom preserves its current scale. Calendar-free navigation retains previous, next, and Today.
- Prototype comparison controls and launch extras were removed from the application.

Validation: Android debug build and two instrumented tests pass (concurrent preference persistence and timeline pinch behavior). Focused web tests cover visibility, persistence, storage failures, and tracking continuity. Browser interaction checks cover independent toggles, retained zoom, reload persistence, Escape/focus return, and access to Stop without stopping. Native preferences also survived force-stop/relaunch. The broader TodayPage suites have 19 failures and 13 passes, reproduced identically on untouched baseline `ee24fb4`.

Review uses sample data: API `127.0.0.1:12015`, Android emulator `emulator-5580`, web `http://127.0.0.1:12017/day/2026-09-14`. Start the web from `frontend` with `VITE_API_PROXY_TARGET=http://127.0.0.1:12015` and `node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 12017 --strictPort`. The native build uses the existing debug API override described below. Sample mutations are limited; this is not a full backend environment.

Final native evidence: [dialog](day-visibility-172/implemented-default-options.png), [Day view](day-visibility-172/implemented-defaults.png). Historical visual checks include small phones and light/dark themes. The retained native review instance uses the approved defaults.

## Historical exploration

Source: GitHub issue #172 (including acceptance criteria), plus the user's explicit inclusion of timeline Zoom. The user liked the first interaction prototype and requested visual refinement of the Day view dialog.

## Accepted intent

Independently show or hide Calendar, Activity Tracking, and Zoom. Hidden sections reclaim their space. Selected-day context, date navigation, restoration, and access to stop a running activity remain available. Visibility never changes recording or timeline scale.

## First question

Does a persistent View button and a compact Tracking shortcut make hiding and restoring controls sufficiently easy on a phone?

The experiment runs inside the full Android app on branch `codex/issue-172-prototype`, in `C:/Users/Caius/Desktop/timebox-172`. It uses the real Day header, Activity Tracking component, timeline, navigation, and stop sheet against an isolated in-memory sample API. The older DayHeaderPrototypeActivity explores Today versus Plan and is unrelated to this request.

Current and customizable layouts share the same date, data, and zoom. The bottom EXPERIMENT button compares them; it is not proposed product UI. The current layout retains the comparison bar so the space comparison is fair. The prototype starts with all sections visible solely to demonstrate independent hiding; this is not an accepted default.

In the proposal, View remains beside Plan. Calendar visibility removes the week/month calendar but retains Today, previous/next navigation, and the date title. The proposed title uses “Mon, Sep 14”: the long month name hid the day number at 360 dp once View was added. The full date remains in accessibility semantics. Hidden Activity Tracking is restored through a Tracking shortcut in that navigation row; a dot identifies a running activity. View also restores each section independently. Zoom visibility does not reset scale. ActivityTracking remains composed with its controls suppressed so lifecycle refresh and recording behavior continue.

Recommendation to review: retain an explicit running-activity shortcut rather than requiring users to remember where a hidden recording lives. Its cost is horizontal header space. Date-title crowding on smaller widths needs visual checking.

## Scope and limitations

- This round addresses native phone layout first. Web source has been inspected; web visual adaptation and verification remain required before design completion.
- Approved first-use defaults: Calendar shown, Activity Tracking hidden, Zoom hidden. Visibility preferences are approved as device-local and remembered across visits and app restarts; they do not sync between devices. The prototype does not yet implement durable storage.
- Additional coverage: month view, long dates, larger text, planning mode, hidden tracking with check-in/error/offline states, stopped tracking, and zoomed timeline. These must not be inferred approved from a successful normal-state review.
- Sample API supports reads and a simulated stop only. Other mutations are not representative. Restart the sample service to restore its running activity.
- Root checkout changes were preserved. Its ActivityTracking diff changes error wording and import order; it was not copied into this isolated branch.
- No existing ADR conflicts were found. No domain terminology changed.

## Historical launch

The one-off sample server and review scripts were removed from the current tree after implementation. To reproduce this historical experiment, use the preserved prototype commit `4fb4746`, which contains `scripts/prototype-172/server.py`. Its sample API used port 12015; the Android build used `./scripts/android-gradle.ps1 :app:assembleDebug '-PreviewApiBaseUrl=http://10.0.2.2:12015/'`.
Acquire a device through `scripts/android-emulator.py`; install the built APK and launch `com.timebox.android/.MainActivity` using the returned token. Device ownership instructions in `docs/agents/android-emulators.md` apply.

For a directly addressable starting layout, force-stop this review instance and launch MainActivity with `--es issue172Layout current` or `--es issue172Layout custom`. The in-app comparison button then preserves compatible visibility and zoom state across switches. Experimental UI is gated from release builds.

Historical review used emulator-5580 at standard 1080×2424 and density 420, with all three sections hidden and a running sample activity. These details describe that review session, not a current reservation; acquire a fresh reservation when reproducing it.

## Review task

Hide all three sections with View, compare the visible timeline with the current layout, navigate to another day, then find the running activity and open Stop without confirming. Decide whether the persistent Tracking shortcut is useful enough to keep, or whether restoration through View alone would be preferable.

## Verified round-one evidence

The debug APK builds successfully, and the final build was installed and launched from this worktree. `git diff --check` passes. Native UI checks exercised all-hidden restoration, independent Zoom and Calendar restoration, comparison switching, previous/next navigation, and reaching the existing Stop sheet through the Tracking shortcut. No stop was confirmed. Sample activity ID 201 remains running with cursor 1 after visibility changes. This is prototype interaction verification, not production integration validation.

| Phone profile | Current lane-header top | All-hidden lane-header top | Space gained |
| --- | ---: | ---: | ---: |
| 360×720 dp (1080×2160, density 480) | 384 dp | 159 dp | 225 dp |
| ~411×923 dp (1080×2424, density 420) | ~386 dp | ~159 dp | ~226 dp |

The gain is approximately three additional timeline hours at 1×. Comparison controls consume the same height in both layouts. Screenshots show the same scenario; normal live elapsed time advances between captures.

- Small phone: [current](day-visibility-172/small-current.png), [all hidden](day-visibility-172/small-hidden.png), [toggles](day-visibility-172/small-controls.png).
- Independent restoration: [Zoom only](day-visibility-172/small-zoom-only.png), [Calendar and Zoom visible, tracking hidden](day-visibility-172/small-tracking-hidden.png).
- Larger phone: [current](day-visibility-172/large-current.png), [all hidden](day-visibility-172/large-hidden.png).

The user liked the first-round interaction. Later decisions below supersede the initial open questions. Web adaptation and the remaining difficult states are still open.

## Round 2 — dialog visual treatment

User feedback: “love it, but the aesthetic of the day view has to be improved,” with a screenshot of the visibility dialog. Scope clarified explicitly: “Focus on the dialog.” Retain the first-round visibility interaction and surrounding Day screen for this round. Defaults and persistence remain unsettled.

The original dialog mixed default Material typography and gray container styling with Timebox's smaller custom kicker. The approved treatment uses the existing Manrope headline, Inter labels, background surface, subtle row icons and separators, blue on-state switches, and a full-width charcoal Done action. Each whole row is toggleable and exposes switch semantics. The recording reassurance stays below the three options. The content can scroll while Done remains accessible when space is constrained.

One focused direction is used because the established Timebox typography and color tokens give a clear starting point. No new palette or app-wide visual system is introduced. The user explicitly approved this aesthetic: “Beautiful. That's, that's exactly the aesthetic that I want.” Preserve this treatment in subsequent rounds. This approval concerns the dialog aesthetic; web adaptation and production implementation remain unresolved.

## Persistence decision

The user selected “per device” when asked whether visibility choices should be remembered on each device or shared. Remember Calendar, Activity Tracking, and Zoom visibility locally across visits and app restarts, independently on each device. These are presentation preferences, not shared tracking state. Web storage should remain local to its browser profile. Durable storage is an accepted design requirement, not yet implemented in this prototype. The user subsequently chose these first-use defaults: Calendar shown, Activity Tracking hidden, Zoom hidden. Apply them only when the device has no saved visibility preferences; preserve existing choices thereafter. Hiding Activity Tracking controls does not disable recording, and the approved compact Tracking shortcut remains available. These defaults are recorded for the next prototype update; they have not yet been applied to the running review instance.

Preview: [refined dialog](day-visibility-172/dialog-refined.png). Original: [round-one dialog](day-visibility-172/small-controls.png). Implementation is isolated in `DayViewOptionsDialog.kt` on the same prototype branch.

Verification: final APK builds and was installed from this worktree. Calendar and Zoom were switched off independently; Activity Tracking was toggled off and back on through its row. Done dismisses the dialog and reopening preserves those choices. Visually checked at the standard profile and at 360×720 dp in both [light](day-visibility-172/dialog-refined-small.png) and [dark](day-visibility-172/dialog-refined-dark.png) themes. Standard display size and light mode were restored for review. Emulator-5580 is retained with the refined dialog open; Calendar and Zoom are hidden, Activity Tracking is visible, matching the user's reference state. Awaiting visual feedback.
