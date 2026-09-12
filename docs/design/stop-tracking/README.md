# Android Stop Tracking — approved design A

The user selected revised prototype A (Block details) for implementation in this worktree. Primary source: `codex/prototype-stop-tracking`, commit `aef01e7`. The prototype variants and review controls remain on that branch.

The production sheet uses the Activity Tracking kicker, light heading, teal Actual badge, recorded time range, duration, and activity name. Now, 15 min ago, and Choose time remain available. Cancel and the confirmation action stay outside the scrolling content. The sheet states that subsequent time is unrecorded.

The real Reporting Time Zone and activity timestamps drive the preview. Overnight ranges include dates; offset changes show both offsets. Custom date/time and repeated-hour selection reuse ActivityTimeField. Invalid, future, or pre-start times disable confirmation. Repository command semantics and the observed activity identity are preserved; busy submission disables controls. No prototype undo or sample state is introduced into production.

Implements the Android presentation requested in issue #161. No web UI change.

Validation: 15 targeted JVM tests passed (preview, reporting-time, and repository behavior), plus both Android stop-flow checks (immediate stop and backdated stop with Cancel). Built and installed the production app from this worktree, then opened the live Stop Tracking sheet on emulator-5554 for visual review.
