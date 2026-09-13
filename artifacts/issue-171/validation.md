# Issue #171 validation

- Web: 38 targeted tests passed across duration formatting, Activity Tracking, Work Mode, reporting-day display, and activity corrections.
- Web: production build passed. Changed-file lint passed except the existing mixed component/helper export in ReportingDayActuals.tsx (react-refresh/only-export-components).
- Android: ElapsedDurationTest and WorkModeTimeTest passed (3 tests). Debug app and instrumentation APKs built successfully.
- Broader Android unit run was stopped after Work Mode lifecycle failures/timeouts in DayWorkModeViewModelTest; those tests do not exercise duration rendering.
- Emulator instrumentation was inconclusive: the first run was interrupted by another review app installation; the retry stalled before assertions. These UI tests are not reported as passing.
- Browser fixtures verified three-hour Current Activity and Focus wording at 360px width. web-focus-narrow.png and web-current-narrow.png show the result. Fixtures ran only in an isolated browser session; routes and local storage were cleared afterward.
- Web review server runs this working tree on http://127.0.0.1:5176 against the existing API on port 8001.
- Installed Android APK SHA-256 matched this working tree's build: 0D13F985476B2F8C5354A9D78EE3A5556634FA62BBD45FA747CB1E1A3F1056A4.
- Android review remains blocked: MainActivity launch repeatedly timed out and produced ANRs on the shared emulator even after precompiling the APK. The ANR report showed 96–98% total CPU, substantial kernel/IO wait, and kswapd memory-reclaim activity. The installed build is verified, but a usable Android review screen was not confirmed.
