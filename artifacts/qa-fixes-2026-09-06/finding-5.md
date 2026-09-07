# Finding 5 — Android unset priority labels

Implemented and visually verified on the Pixel_9a Android 16 emulator.

## Cause and change

`TaskDetailPriorityTile` in `android/app/src/main/java/com/timebox/android/ui/battleplan/BattlePlanScreen.kt` put Importance and Urgency into two narrow weighted columns with `maxLines = 1`. The value `Not specified` exceeded the available width and Compose clipped it to `Not`.

The focused change stacks the two fields within the existing Priority card, permits labels/values to wrap, and replaces the fixed 120dp height with a 120dp minimum. Typography, category labels, edit callbacks, menu choices, and accessibility descriptions are preserved. One source file changed: 8 insertions, 8 deletions.

## Reproduction and visual verification

Used existing QA task 12, `QA coordinator finding1`, with both priorities unset. No application data was changed. Screenshots and UI hierarchies are alongside this report:

- `finding5-before.png` / `.xml`: original defect reproduced at 1080×2424, density 420, font scale 1.0; both visible values stopped at `Not`.
- `finding5-after-default.png` / `.xml`: final build, original dimensions/settings; both full values fit on one line.
- `finding5-font13.png` / `.xml`: final build, original density with font scale 1.3; both full values and category labels visible.
- `finding5-narrow-font13.png` / `.xml`: final build, density 480 (360dp logical width), font scale 1.3; both values and labels visible without clipped or split words. Scrolled to bring the entire card into view.
- `finding5-edit.xml`: existing `Change importance` / `Change urgency` semantics and edit values remain present.
- `finding5-urgency-menu.png`: tapping Urgency opened Clear urgency / Low / Medium / High. Dismissed without selecting or saving.
- `finding5-review.png` / `.xml`: final restored review state, task 12 read view with both full `Not specified` values.

The visual loop was ADB navigation plus `adb shell screencap -p /sdcard/finding5-before.png`, `adb pull`, and image inspection. Hierarchy-only assertions cannot catch this defect because the original semantics already contained the full text; no style-mirroring unit test was added.

## Checks and build

- `./scripts/android-gradle.ps1 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: BUILD SUCCESSFUL; 146 unit tests, zero failures/errors, lint passed. This run validated the wrapping/minimum-height fix before the final inner Row-to-Column refinement.
- `./scripts/android-gradle.ps1 :app:assembleDebug :app:assembleDebugAndroidTest`: final source BUILD SUCCESSFUL, 17 seconds.
- Installed final application and test APKs with `adb -s emulator-5554 install -r`, preserving application data.
- Final-source native instrumentation: `adb -s emulator-5554 shell am instrument -w -e class com.timebox.android.ui.battleplan.BattlePlanScreenTest#taskDetailsEditModeRevealsFieldsAndForwardsDashboardChanges com.timebox.android.test/androidx.test.runner.AndroidJUnitRunner` → `OK (1 test)`. Verifies Ready to Plan and Importance edit callback/menu interactions.
- Final APK: `C:/Users/Caius/Desktop/timebox/android/app/build/outputs/apk/debug/app-debug.apk`.
- Test APK: `C:/Users/Caius/Desktop/timebox/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- No backend or web source changes for this finding; no listener changes, commits, push, merge, or deployment.

## Final state and limitations

Restored `wm density reset` and `settings put system font_scale 1.0`. Verified physical size 1080×2424, physical density 420 with no override, font scale 1.0. The updated app is running in task 12 read view. QA fixture remains open, importance/urgency null; no test-created data or unsaved edits remain from this finding. Existing task 1 was untouched. Emulator ownership released to the coordinator for independent review.

Visual verification covers portrait Android at default and 1.3× text, including 360dp logical width. Physical devices, landscape/tablet layouts, and TalkBack interaction were not tested.

Coordinator acceptance: reviewed one-file Kotlin diff; independently captured screenshot coordinator-f5.png on final installed APK. At physicaldensity420/font1.0 both Importance and Urgency show full Not specified. Verified package lastUpdateTime and defaultsrestored. Final original visual reproduction passes. Finding5 accepted; allfive accepted sequentially before final cleanup/smoke.
