# Android Assistant — quiet conversation

The user selected variant A on 24 September 2026 and requested implementation.
The question was whether conversation, the answer, or the plan should lead.
The selected layout keeps conversation first and the plan inline before the answer.

Primary design source: [prototype branch](https://github.com/crimsoncaius/timebox/tree/codex/prototype-assistant-panel),
commit `d6504662`, variant A in `frontend/src/features/assistant/AssistantPanel.prototype.tsx`.
The browser comparison, alternatives and sample data remain on that throwaway branch.
Implementation is tracked in [#286](https://github.com/crimsoncaius/timebox/issues/286).

The native implementation uses a compact title and temporary-conversation disclosure,
an accessible New conversation icon, compact user bubbles and open Assistant answers.
The inline plan card keeps the date, original read time and Reporting Time Zone;
start and end times stack beside Block Name and Task Type. Three rows appear initially
and expand in place. Larger text puts the full time range above the row identity.

The welcome screen has editable starter rows. A rounded multiline composer provides
explicit Send and Stop icons, accessible action names, a disabled empty Send, and the
existing 4,000-character limit. The follow-up suggestion fills the editable composer.
Incomplete attempts retain their explanation and Retry. Expiry, acknowledgement,
reset and scroll-following behavior remain owned by the existing controller and screen.

This supersedes the earlier visual treatment only. The approved card-before-answer
contract in `docs/specs/assistant-redesign-handoff.md` remains in force. There are no
backend or protocol changes and no changes to Planned Blocks on Day.

## Validation

- Android debug APK built successfully; all 322 unit tests passed.
- The existing Assistant instrumentation case now locates the accessible Send icon
  and checks the new welcome copy, editable follow-up, plan expansion and reset.
- A second case checks multiline drafting while a response is active, Stop, and reset.
- The full instrumentation APK has an unrelated compile failure in
  `BattlePlanScreenTest.kt`, which references removed Trash undo classes. An ignored
  local Gradle init script excludes that file when building the focused Assistant suite.
- After the user freed emulator disk space, both focused Assistant device tests passed
  on a fresh managed emulator. The first attempt crashed during Android/Google Play
  first-boot initialization before running tests; the settled-device run passed both.
- The plan expansion, editable follow-up and reset case also passed at 1.5x system
  font scale in dark mode. Native screenshots confirm the plan and composer remain
  readable. The review device was restored to normal text size and light theme.

## Native review

- Device: `emulator-5642` (`timebox-agent-32`), retained for user review.
- Reservation: `cd421fba690c494cb3fd7ac5a882e6b1`.
- Installed APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
- The actual application is open on Assistant. Its default backend is port 8001.
- Local screenshots: `artifacts/assistant-panel-a/assistant-a.png` (app welcome),
  `artifacts/assistant-panel-a/assistant-a-plan.png` (native sample plan), and
  `artifacts/assistant-panel-a/assistant-a-dark-large-plan.png` (native large-text dark sample plan).
  Plan screenshots use deterministic instrumentation fixtures; the application
  itself uses its ordinary backend and contains no mock response data.

The user approved merging `codex/assistant-panel-a` into `master` on 24 September 2026. The prototype remains separate, and progressive streaming is tracked in #287.

## Review correction: keep navigation while drafting

The user reported that Assistant filled the screen and its bottom navigation
disappeared after using the composer. Native inspection confirmed the navigation
nodes were absent while Android reported the input method as active, including
the floating handwriting toolbar. The shared keyboard policy hid the entire bar.

Assistant now keeps bottom navigation visible with input active. The surrounding
layout continues to apply keyboard insets, keeping the composer and navigation
above a docked keyboard. Other screens retain their existing keyboard policy.

The full-app `assistantKeepsNavigationWhenInputMethodIsVisible` device regression
failed on the original rule at the navigation visibility assertion, then passed
after the fix. It also verifies the composer action remains visible and navigation
back to Day works. The focused AppRoutes unit tests and debug build pass.

