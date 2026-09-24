# Use Monday-based calendar weeks throughout Timebox

## Decision

Timebox uses Monday through Sunday throughout web and Android, regardless of device locale. The Day and Chronicle month grids, every app date picker (including native Android dialogs), Trends weeks, weekly quota periods, and scheduled weekly recurrence all use that convention. The web week-start setting is removed; it no longer affects the API or recurrence generation.

Weekly periods use calendar dates in the Reporting Time Zone. For example, at `2026-09-20T16:30:00Z`, Today is Monday, September 21 in `Asia/Singapore`, so the current Trends and quota week is September 21–27. A quota that starts on Wednesday, September 23 has its first effective period September 23–27; later periods run Monday–Sunday. A scheduled rule beginning Wednesday, September 23 with a Monday weekday first generates Monday, September 28, and repeats from Monday-based weekly anchors.

## Migration

The schema migration drops `app_settings.week_start`. Existing historical quota records retain their recorded dates and completion history. Future generation uses Monday boundaries. This personal app has no current quota tasks requiring a data rebalance; no historical quota periods are rewritten.
