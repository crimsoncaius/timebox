# Recording Actual from a plan: progressive prototype

## Context

The behavior in [the agreed specification](../../specs/issue-173-record-actual-as-planned.md) is implemented. This exploration concerns how users understand and operate it, starting with the overlap warning.

Settled: keep the plan, copy saved details, record through now or the planned end, allow repeated recording with explicit replacement, preserve outside portions and ongoing tracking, keep Task Completion separate, and provide atomic Undo. These remain stable across presentation experiments.

## Design map

1. **Understand replacement before confirming.** Before/after timeline accepted and integrated on Android and web.
2. **Find and understand the action.** Keep the accepted placement and underway/past labels in Planned Block details.
3. **Recognize the outcome and recover.** Connected Android flow accepted; exact-repeat feedback corrected inside its sheet. Web split and Undo verified against the isolated API.
4. **Handle difficult states.** Multiple conflicts, running tracking, stale previews and cross-midnight intervals covered by focused tests; web narrow screens, keyboard use and dark mode checked. Large Android font scaling and screen-reader walkthroughs remain unverified.

These are coverage areas, not a fixed sequence of rounds. Feedback on the first experiment may change the next decision.

## Round 1: communicating replacement

The current dialog repeats dates and describes every operation in prose. The app's Stop Tracking flow already uses a bottom sheet, structured time summaries, timeline context, and fixed footer actions. That provides a grounded direction for exploration.

Proposed experiment: compare a concise outcome summary with a before/after timeline inside the same app sheet. Keep the scenario, times, notes, confirmation meaning, and recovery behavior constant. Compare how easily each reveals the preserved fragment and replaced interval.

Recommendation to test: the before/after view, because this decision is primarily about time boundaries. Its trade-off is more vertical space; a summary may be easier for simple overlaps.

Android first and the before/after presentation are accepted. The user reviewed the split scenario in the emulator and said “good I like it”. Keep the native sheet, paired timeline, visible replacement range, preserved outside fragments, metadata disclosure, and fixed confirmation actions.

## Accepted Android integration

The normal Android overlap flow now uses this design with the server's real conflict list and frozen interval. `RecordingTimelineModel.kt` derives both lanes, including multiple conflicts and a continuing Current Activity. Exact ranges below the diagram cover small fragments, dates, and time-zone offsets; names and notes remain available in full. Stale previews, busy actions and errors remain in the same sheet. Comparison controls require the explicit prototype build flag and are absent in the current review build.

The connected walkthrough found that exact-repeat feedback was hidden behind the open Planned Block sheet. Recording feedback now appears inside that sheet, alongside linked Actual access and Undo.

Validation: five timeline-model tests cover spanning, multiple, running, empty/stale and subsecond cross-midnight intervals; recording ViewModel tests cover confirmation, the frozen endpoint, cancellation and Undo. Native replacement created the expected linked Actual; a repeat kept the same ID; Undo restored the pre-review record's exact times, name, note and link. The existing API/recording tests cover underway eligibility and frozen-time behavior. The user accepted the connected Android flow (“it's good”).

Reproduction uses the command below with `-PrecordingPrototype=false`. The historical review opened the real overlap preview for September 13 → Write chapter. Its Earlier draft was 10:11–10:41 (the review data was edited after the original screenshot), so this case showed full replacement rather than splitting. That device was retired on 2026-09-26. Historical comparison screenshots and the opt-in experiment remain available below.

## Web integration

The user delegated web decisions without another approval round. Use the accepted information hierarchy in the existing native HTML dialog: paired timeline, exact interval details, metadata disclosure, a scrollable body and fixed footer. Keep the app's light/dark colors and support narrow windows. Keyboard Tab/Shift+Tab wraps inside the dialog; Escape cancels except during a save.

Review URL: http://127.0.0.1:12019/day/2026-09-12?block=614491471. This separate review day preserves any ongoing Android review edits. The visible browser is left in the before/after warning. An ordinary fresh visit opens Planned Block details; choose Record Actual as planned to open the warning.

Validation: 46 focused web tests pass across preview, Day and block-inspector flows; the final six preview tests pass again after keyboard handling was added. Type-check, production build and changed-file lint pass. Browser checks cover desktop, 390×844 mobile, dark mode, visible fixed confirmation, no horizontal dialog overflow, metadata disclosure, Escape, and keyboard wrapping. Real replacement creates 09:45–10:00 / 10:00–11:00 / 11:00–11:15 with the correct notes and plan link; Undo restores the original spanning record and ID. Screenshots: `artifacts/issue-173/accepted-web-desktop.png`, `accepted-web-mobile.png`, and `accepted-web-dark.png`.

The design decisions requested in this exploration are settled. Prototype controls remain opt-in only; no further approval is required for web.

## Working Android experiment

The debug-only `RecordingPreviewStudy.kt` uses the existing Planned Block action and a native bottom sheet. The clearly separated prototype controls switch Summary / Before-after and Trim / Split / Tracking. Replacement and Undo are local simulations; Reset restores the selected scenario. Compatible scenario state survives presentation switches. Ordinary builds default `RECORDING_PROTOTYPE` to false.

Build from this worktree:

```powershell
$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
./scripts/android-gradle.ps1 :app:assembleDebug -PrecordingPrototype=true -PreviewApiBaseUrl=http://10.0.2.2:12018/ -PreviewApplicationIdSuffix=.issue173
```

The historical review used `emulator-5586`, package `com.timebox.android.issue173`, isolated API port 12018. Acquire a fresh managed device and start the isolated API to reproduce it. Navigate Day → September 13, 2026 → Write chapter → Record Actual as planned. With an owned reservation, each presentation is directly selectable (substitute the reservation token; change `timeline` to `summary`):

```powershell
python scripts/android-emulator.py adb TOKEN shell am start -f 0x20000000 -n com.timebox.android.issue173/com.timebox.android.MainActivity --es recording_preview_variant timeline
```

Review task: select Split and identify which parts of Earlier draft survive. Compare both views, inspect the name/note disclosure, then try replacement and Undo. Recommendation remains Before-after for spatial clarity; Summary exposes the details disclosure with less scrolling. Both retain fixed confirmation actions.

Verification: debug build succeeds; both presentations and intent selection work in the actual app. Trim, Split, Tracking, simulated confirmation and Undo were exercised on the reserved device. Screenshots are in `artifacts/issue-173/prototype-*.png`.

Coverage deferred from this focused experiment: multiple conflicts, stale previews, long text/font scaling, midnight transitions, and real failure/Undo invalidation presentation. Production recording behavior is covered separately by the implementation tests. The prototype tracking case is illustrative and does not start a real tracker. Entry hierarchy and connected success flow remain future design decisions; the simulated result is only enough to repeat this comparison.
