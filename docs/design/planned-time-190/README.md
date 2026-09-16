# Planned time entry and Record Actual

Issue: [#190](https://github.com/crimsoncaius/timebox/issues/190)

Android presentation is implemented from this exploration. Web is deferred. Matching times are already recorded; that rule is now in [#173](../../specs/issue-173-record-actual-as-planned.md).

## Context

Android Actual Block details already lead with a large recorded range, elapsed duration, and compact Start/End controls. Planned Block details used the older Start/End fields, a duration pill in the chrome, a plain Record Actual action, and a dense “Actual recorded” status line.

## Design map

1. **Share one time hierarchy.** Accepted: Planned details use the Actual range, duration beside it, and compact Start/End.
2. **Present Record Actual and linked recordings in that language.** Accepted: Record Actual as an Actual-colored interval card. Same times is already recorded. Edited override is **On this Actual**.
3. **Present the source plan on the Actual Block.** Accepted: Planned-colored range card; caption is “already recorded” or “times differ.”
4. **Bring web to the same hierarchy.** Deferred.

## Product rule: matching times means already recorded

If a linked Actual already matches the requested interval, make no mutation and do not copy the plan's current name or note. Different times still replace through the #173 overlap confirmation, which copies the plan's current saved details.

## Accepted treatments

- Interval time entry on Planned Block details.
- Record from this plan as an Actual interval card.
- Already recorded: Open Actual only; plan notes stay on the plan.
- Edited: On this Actual (current interval struck through, plan interval as the replacement).
- Replace confirmation lists every overlapping Actual.
- Source plan on the Actual Block uses the same time card.
- Space the recording block as its own section above Name.

Working Android code: `PlannedTimeStudy.kt`, `PlannedRecordingState.kt`. The in-app prototype switcher is removed.
