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

Built and relaunched on emulator-5584, retained for user review under token
`f1d1a00af63d469fa413a18c8dc87710`. Verified opening the one-minute Actual Block,
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
