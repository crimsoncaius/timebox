# Habits view — issue 214

Android-first Chronicle view showing each Habit's Habit Periods for one Calendar Week. Domain terms (Habit, Habit Period, Habits) are in `CONTEXT.md`; backdated habit ticks are ADR 0015.

## Accepted before prototyping

- A Habit is an opt-in on a Recurring Task Series, with no lifecycle of its own. History from before opting in counts, and opting out deletes nothing.
- Only a Task Completion counts. Tracked time alone does not.
- A tick on a past day records a Task Completion dated that day and reverses a Skipped Task Occurrence. There is no late outcome.
- Grid: rows are Habits and columns are the days of one Calendar Week.
  - Scheduled rows tick due days only. Days that aren't due are inert.
  - Quota cells show that day's completion count, and extra sessions beyond the quota are allowed. Surplus never carries forward.
  - Monthly habits show month-to-date progress.
- Paused and ended weeks omit the row, but past active weeks keep it. Deleted series vanish everywhere.
- Opt-in lives on the routine sheet and in an Add habit picker in the view.
- No streaks, consistency percentages or other gamification in v1.

## Round 1 — row layout at phone width

Question: does a name column beside the week fit a phone, or should the name sit above full-width cells?

Stable across both layouts:
- Chronicle chrome with a third Habits tab.
- Week arrows plus a This week control.
- The cell language: filled `actual` for met, with a check or a count; a neutral cell with ✕ or `0/2` for missed; a pale fill for a partial daily quota; a `planned` outline for today; an outline for upcoming days; a dot for days that aren't due; a dash for excused days.
- A week total per row.
- Tap to record a day, long-press a quota day to open a −/+ session sheet, Add habit, and the empty state.

What varies:
- **Name column:** a 92dp name column with the cadence under the name, 7 cells of about 30dp by 40dp, and a week total column.
- **Name above:** name, cadence and total on one line, with 7 cells of about 50dp by 46dp below.

Samples (current week hand-authored, earlier weeks seeded):
- Gym, scheduled Mon/Wed/Fri.
- Meditate, daily.
- Read, 3× week.
- Water, 2× day.
- Call parents, 4× month.
- Run, Tue/Thu/Sat, paused from Thursday this week.
- Weekly review, Sundays.

Launch:
- `timebox://prototype/habits?total=fraction|labeled|ring|pill&scenario=sample|empty`.
- An in-app switcher strip (#214) changes the comparison and scenario, and it is not product UI.
- The route and manifest filter are debug-only.

Prototype: `android/app/src/debug/java/com/timebox/android/ui/chronicle/HabitsPrototype.kt`.

**Decision: Name column.** The user preferred it, and it is the table they described. Name above has been removed from the prototype.

The user also confirmed that the ~30dp day cells are comfortable to tap, and that done, missed, not-due and paused states are distinguishable at a glance.

Fixed after round 1: a partial daily-quota cell now uses `actual` at 30% alpha, so it stays distinct from an empty cell in dark mode.

## Round 2 — what the right-hand column says

Question: should every row carry a week total, or only rows whose Habit Period spans several days?

What varies (switcher "Totals"; URL `totals=mixed|period`):
- **Every row:**
  - Scheduled and daily-quota rows show periods met out of periods in the week (`5/7`).
  - Weekly quotas show sessions against the requirement (`3/3`, `4/3`).
  - Monthly quotas show month-to-date (`3/4 Sep`).
- **Multi-day periods only:** only weekly and monthly quotas keep a total, because their verdict is not visible in any single cell. Scheduled and daily rows leave it blank, since each cell is already its own verdict.

**Decision: every row.** The user wants a weekly total on every habit, and asked to explore how it looks.

## Round 3 — how the week total looks

Question: which visual treatment makes the total read best, given that it counts days on some rows, sessions on weekly quotas, and month-to-date on monthly quotas?

Every treatment shows the same numbers. Met totals use `actual`. Missed totals (the period ended short) are muted.

What varies (switcher "Total"; URL `total=fraction|labeled|ring|pill`):
- **Fraction:** plain `5/7`, with a month caption on monthly rows.
- **With unit:** the fraction plus a small unit caption (`days`, `sessions`, `in Sep`).
- **Ring:** a progress ring with the done count inside. The target is implied by how full the ring is.
- **Pill:** the fraction in a status pill: filled when met, tinted while open, outlined when missed.

**Decision: With unit.** The user preferred it, and it removes the days-versus-sessions ambiguity of a bare fraction. The total switcher has been removed. The other treatments stay reachable only through the `total=` URL parameter.

## Closing decisions

- **Tapping a habit's name opens its routine details sheet** (#218).
- **No undo for ticks or unticks.** The user judged ticking to be trivially reversible. A second tap restores the Task Completion, dated that day, and re-reverses any Skipped Task Occurrence. The one thing an untick loses is the original completion time, and the user accepted that.
- **Opting out happens only on the routine sheet** (a Track as habit switch). The Habits view has Add habit but no remove control.

## Observations and open questions

- An empty weekly-quota day and a missed scheduled day share the neutral fill. Only the ✕ distinguishes them.
- The prototype cannot open a routine from its row. Opting out from the view is not modelled.
- Not covered: offline or failed ticks, large numbers of habits, font scale above 1.0, and swipe week navigation (Calendar supports swiping between months).

## Verification

- The debug APK builds.
- Checked on emulator-5598 (1080×2424, 420dpi), light and dark:
  - Both layouts, current and previous weeks, the empty state and the session sheet.
  - All four total treatments, this week and last week (comparison sheets `totals-compare-*.png`).
  - Tapped a missed scheduled day to Met. Tapped a quota day to reach 3/3.
  - Long-pressed a daily-quota day to open the session sheet.
- Screenshots are in ignored `artifacts/habits-214/`.

## Production

The spec is posted on GitHub issue #214.

Backend:
- `recurring_templates.track_as_habit` column, migration 036.
- `GET /habits?week=`: one Calendar Week of Habit rows, day states and totals.
- `POST` and `DELETE /habits/{template_id}/days/{date}`:
  - A tick on today uses the normal Task Completion.
  - A tick on a past day records a completion dated that day at local noon. It has no tracking or Planned Block side effects, and it reverses a skip (ADR 0015).
  - A quota tick completes the next open Session Task. Once none are open, it creates an extra Session Task.
  - An untick reopens the completion. Extra sessions are removed first, by soft delete. Ended periods become skipped again on the next synchronization.
- Quota Trackers now count as complete when completed sessions are at or above the requirement.

Android:
- A third Chronicle tab, Habits (`HabitsScreen`, `HabitsViewModel`). It uses the name-column layout and a total with its unit on every row.
- Week navigation uses arrows, This week and swipe.
- Tapping a day ticks or unticks it. Long-pressing a quota day opens a −/+ session sheet.
- Also included: the Add habit picker, the empty state, and tapping a name to open routine details.
- The routine sheet has a Track as habit switch.
- The debug prototype stays at `timebox://prototype/habits` as design reference.

Validation:
- Backend: 9 new Habits tests. The full suite passes (527 passed, 6 skipped).
- Android: 4 new view-model tests; the Chronicle, Battle Plan and patch-encoding suites pass (99 tests).
- Exercised on a managed emulator against an isolated SQLite API (port 12214, `artifacts/habits-214/review.sqlite`, seeded by `artifacts/habits-214/seed.py`):
  - A past scheduled tick reached 3/3 days, and a quota tick today added a session.
  - The long-press sheet's + recorded daily-quota sessions.
  - Add habit opted a routine in, and its prior history appeared.
  - Tapping a name opened the routine, and switching Track as habit off removed the row.
  - Swiping moved to the previous week.
  - A past untick and re-tick made the day missed, then met.
- Review APK: `-PreviewApiBaseUrl=http://10.0.2.2:12214/`.
- Fixed during review: toggling Track as habit made the routine sheet close and reopen. The sheet state was keyed on a confirm callback that was rebuilt on every save. The routine calendar also collapsed to a loading bar on each refresh. Both affected every routine-sheet edit, not only the habit switch.
- Not exercised: instrumentation tests, process death, font scale above 1.0, and dark mode on the production screen.

