# Issue #173 implementation and review

Implemented the agreed individual Planned Block recording flow on web and Android: partial recording until now, full recording after the planned end, repeated recording, exact-match feedback, explicit overlap replacement, preserved outside portions, and atomic Undo. Planned Blocks and Task Completion remain independent of recording time.

Replacement and Undo participate in the Activity Tracking journal. Preview fingerprints protect against concurrent changes, the endpoint freezes the requested end, and continuing Current Activity time is preserved. Undo snapshots use the additive `029_planned_recording_undo` migration; existing installations must apply it before running the updated backend. The migration does not perform a cutover or modify existing activity records.

## Verification

- Backend: **314 passed, 6 PostgreSQL-only tests skipped**. Tests cover partial/repeated/exact recording, multiple and spanning conflicts, stale previews, frozen endpoints, disjoint and overlapping tracking, independent lifecycle changes, replay, and safe Undo.
- Web: **63 focused tests passed** across Day, inspector, preview, tracking, and correction suites. The final exact-repeat feedback change passed **23 tests**. TypeScript, production build, and changed-file ESLint passed.
- Android: debug APK builds, ActivityRepository unit tests pass, and **2 new recording ViewModel tests pass**, covering save-before-record, frozen confirmation, cancellation, and Undo.
- Migration: upgrade from 028, downgrade to 028, and upgrade to 029 passed on an isolated SQLite database. Existing migration regression tests also pass.
- Real browser and Android: previewed replacement of an existing 09:45–10:15 Actual with a planned 10:00–11:00 block, preserved 09:45–10:00, then used Undo to restore the original record. Both clients read the restored shared state.

## Running review

- Web: [September 12 Day](http://127.0.0.1:12019/day/2026-09-12?block=614491471), Playwright session `issue173`, accepted before/after warning open.
- API: `127.0.0.1:12018`, isolated `backend/issue173-review.sqlite`, Reporting Time Zone `Asia/Singapore`.
- Android: `com.timebox.android.issue173` on **emulator-5586**, retained for user review.
- Android's connected flow was accepted by the user. Web uses a separate review day so continued Android review does not change its example. Both still share the isolated API.

## Accepted presentation follow-up

See [the design record](../../docs/design/record-actual-173/README.md). The before/after prototype was accepted and integrated into real Android and web recording. The warning shows preserved intervals, changed metadata, running tracking and refreshed previews, with fixed confirmation actions. Exact-repeat feedback is visible inside Android's Planned Block sheet. Prototype controls remain opt-in and absent from ordinary builds.

Follow-up validation: seven focused Android tests pass; 46 focused web tests pass, with six preview tests rerun after keyboard handling. Type-check, build and changed-file lint pass. Native recording/repeat/Undo and web spanning replacement/Undo were verified against the API. Desktop, narrow web, dark mode and keyboard checks passed. Updated screenshots use `accepted-android-preview.png` and `accepted-web-*.png`.

The user explicitly requested an additional emulator because the standard pool was full. The helper now supports explicit `acquire --extra` while preserving ordinary capacity and existing reservations. A unit test verifies unique allocation, retained ownership, and compatibility with another helper instance. The additional device was acquired, installed, launched, inspected, and retained through the helper.

Screenshots: [web](web-review.png), [Android](android-preview.png). Port configuration is in [review-config.json](review-config.json). The review seed script targets only this isolated API.

No production deployment or GitHub changes were made.
