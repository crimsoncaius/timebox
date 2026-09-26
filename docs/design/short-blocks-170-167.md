# Accepted implementation — issues #170 and #167

> Superseded in part by [issue #241](https://github.com/crimsoncaius/timebox/issues/241): Android and web no longer show a standalone Zoom bar or a Zoom visibility switch. View retains the current scale and Reset zoom; web keyboard zoom adjustment now lives in View. Calendar and Activity Tracking visibility remain independent. Legacy zoom visibility preferences are ignored.

The approved prototype is preserved at commit `4e6af5a` on
`codex/issues-170-167-prototype-reference`. The production implementation replaces
its debug route with the real Android and web Day timelines.

- Blocks use their actual duration, including one-minute Planned Blocks.
- Planned strips are blue; Actual strips are green. Text appears at 22dp and
  resize grooves at 64dp on Android (equivalent CSS pixels on web).
- Both lanes share anchored pinch zoom, bounded at 0.5–12×. The toolbar shows
  Zoom and Reset without plus/minus buttons. Reset changes only zoom.
- Tapping opens details without zooming. Exact start/end fields support blocks
  too small to resize directly. New blocks still default to 30 minutes.
- Active resizing continues below the groove threshold. Running Actual Blocks
  grow with elapsed time and retain their dedicated editing flow.

Validation: 51 focused web tests and the production web build pass; 34 focused
backend tests pass; targeted Android unit tests and the two-finger device test
pass. Fifteen failures in the broader web selection reproduce unchanged at
baseline `452fa67`; the full suite is not claimed to pass.

Review runs against isolated sample data at API port 12012 and web port 12013.
Android uses the separate debug package `com.timebox.android.shortblocksreview`
on emulator-5584. The normal app's data is preserved.

The original experiment record below is historical; its open questions and
prototype-only launch instructions are superseded by this implementation.

---

# Short blocks and timeline zoom — first experiment

Issues: #170 (presentation and interaction), #167 (zoom).
Branch: `codex/issues-170-167-timeline-prototype`, rebased onto local master.

## Accepted direction

- Planned and Actual Block heights represent duration.
- Grooves remain available directly when there is sufficient screen space.
- Small blocks lose grooves, then text as space decreases.
- Tap opens block details using the best position match. No automatic zoom or chooser.
- Pinch enlarges both lanes together; the user can zoom after an inaccurate tap.
- An active resize retains its controls until release, even below the threshold.
- Sub-five-minute blocks are valid. Five minutes is the target for comfortable
  direct resizing at maximum zoom, not a minimum duration.

## Second round — precise editing

Accepted refinement after review: compact blocks use the existing Planned blue
and Actual green instead of the current-time red. Pinch remains the zoom control;
show a non-interactive zoom readout, remove +/− buttons, and retain Reset.

The user accepted continuing with 0.5–12x as the trial range. Zoom buttons now
disable at those limits. One- and two-minute samples supplement the first set.
The local details sheet now has Start/End fields, Save and Cancel. It accepts
minute precision, validates ordering, the sample window, and same-lane overlaps.
The running sample remains read-only. This experiment does not settle whether
production needs seconds-level corrections.

Built and relaunched on emulator-5584 (retired during the 2026-09-26 cleanup).
Acquire a fresh managed device to reproduce this review. Verified opening the one-minute Actual Block,
changing its interval from 10:50–10:51 to 10:49–10:51, saving, and reopening
with the two-minute duration. Samples reset afterward. Pinch and handle comfort
still need user review; the precise editor's keyboard layout needs further
physical-phone verification.

## First experiment

The debug intent extra `timelinePrototype=true` substitutes local sample data in
the existing Day destination, retaining app navigation and the real DayTimeline.
All sample edits stay in memory. Other destinations remain normal application
screens. The first round used a read-only sheet; the second adds local editing.

Provisional thresholds: 22dp for one-line identity; 64dp for grooves. Scale range
0.5–12 times the existing 34dp/30-minute scale. These numbers are for review,
not accepted requirements. At 12x, a five-minute block occupies 68dp.

Samples cover adjacent five-minute blocks, a longer neighbor, isolated short
blocks, a long name, a resize target, and a running Actual Block starting at
one minute with a matching current-time line. The simulated activity grows
once a minute. Reset restores sample edits and scale.

Try tapping consecutive strips, pinch to reveal their names, then zoom until
grooves appear. Resize the 15-minute Actual Block below the groove threshold
and release. Check whether the remaining finger target feels comfortable.

## Open questions and coverage

- Tune thresholds and scale bounds through device review.
- Verify anchored pinch, cancellation when a second finger joins, scrolling,
  and resize continuity on a device.
- Review tiny-block best-match selection; current experiment uses exact card
  bounds and normal Compose hit testing rather than a new selection algorithm.
- Planned Block short-duration validation remains a production follow-up;
  the experiment allows one minute locally without changing saved-data rules.
- Creation, persistence, zoom preference, accessibility
  tuning, and web integration are outside this first round.
- No product preferences are settled by this experiment until user review.

## Launch

Build: `scripts/android-gradle.ps1 assembleDebug` with the Android SDK configured.
Acquire an emulator using `scripts/android-emulator.py acquire --owner ...`.
Use its returned token with the helper to install the debug APK, then:

```powershell
python scripts/android-emulator.py adb TOKEN shell am start -n com.timebox.android/.MainActivity --ez timelinePrototype true
```

Launched on the user-requested additional emulator-5584; the two earlier review
devices were preserved.

Validation: debug APK builds; PlanningDragPlacementTest, PlanningLogicTest,
and DaySwipeTest pass. The full unit run was stopped after two
DayWorkModeViewModelTest failures (restoration assertion and uncompleted
coroutines); it is not a passing full-suite result. The second-round APK also
builds successfully; device checks are recorded above.
