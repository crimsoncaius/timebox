# Shared Activity Reporting Time Zone (#151)

The gated Activity protocol initializes one IANA Reporting Time Zone atomically
from the first connected device. `/activity/reporting-timezone/initialize` is
idempotent: later device-zone changes do not replace it. Explicit
`PUT /activity/reporting-timezone` updates the shared setting and cursor without
rewriting Actual instants, IDs, duration or journal intent. Both Settings screens
expose the preference. Day reads and development health/today use that zone.

Migration 026 seeds an already-enabled Activity installation with APP_TIMEZONE,
which must be set to the prior server reporting zone. An empty fresh database
leaves it uninitialized for the first device. Production legacy migration/cutover
is still #158; this migration does not admit legacy Actuals or deploy anything.

Day projections clip continuous Actual intervals against actual zoned midnight
boundaries. `duration_minutes` is elapsed time, floored to whole minutes, and may
exceed 1440. `day_length_minutes` distinguishes 23/25-hour days from ordinary
second-precision intervals. On clock-change days both clients display selectable
Actual rows with elapsed daily shares and UTC offsets alongside the existing
Planned grid. Selecting one retains the original full Actual identity. Normal
Day grids remain unchanged.

Offline editor contract for #152:
- Web `zonedLocalDateTimeCandidates(local, timezone)` returns chronological ISO
  instants. `zonedLocalDateTimeToIso(local, timezone, occurrence)` rejects gaps and
  requires `earlier`/`later` for repeated times.
- Android `ReportingTime.candidates` / `resolve` use ZoneRules with identical
  rejection and explicit `Occurrence.Earlier` / `Later` semantics.
- Existing minute editors reject ambiguous times; full occurrence controls belong
  to #152. Both helpers require no network once the shared zone is known.

Verification: full SQLite backend 278 passed, 3 PostgreSQL-only skips; full web
258 passed. Subsequent focused backend 39 passed/1 skip and PostgreSQL reporting
4 passed, including simultaneous initialization by different zones. Focused web
conversion/settings/fold-day and Day regressions pass. Android repository and
conversion tests and gated APK build pass; Settings and folded-Day Compose tests
both pass on Pixel 9a API36. The broad Android run reproduces legacy
DayWorkModeViewModelTest restoration failure and coroutine timeout, then its
worker was stopped; it is not an all-green full run.

Isolated `activity_review` was backed up with pg_dump before migration026.
API comparisons retained all20 existing Actual records, all instants/metadata,
and the running identity/start. Shared Settings changed from Singapore to
New York on web and appeared in native Settings. An isolated review record on
2025-11-02 from01:50 -04:00 to01:10 -05:00 shows20m in API, real web Day and real
native Day. Both apps remain running against API12004/web12005, with the
separate Android application `com.timebox.android.activitydev`. Evidence is in
local `artifacts/activity-151-*`. No production data was migrated or deployed.
