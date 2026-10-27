# Review details: Day-style activity sheet

Working proposal, 27 October 2026. Uses the real Review screen with an illustrative
debug fixture; screenshots do not show the user's records.

The previous sheet repeats the activity under original/current headings and puts
the current time range near the bottom. The proposal reuses Day's `TimeboxSheet`:
time range, date, reporting time zone and completed duration lead; identity and
spoken request follow. Original recording is expandable. Open day and Adjust
remain in the pinned footer. Existing offline and sync restrictions still apply.
Open day follows the displayed record's start date, falling back to the original
record when the current record is unavailable.

- [Before](before.png)
- [Proposal](after.png)
- [Proposal at 160% font scale](large.png)

Validation: `assembleDebug` passed. The focused instrumentation test
`AssistantReviewTest#detailsKeepsHeaderPositionAndBackArrow` passed, including
time range, duration, disclosure, and stable background header checks.

Reproduce on a reserved emulator using the repository helper:

```powershell
python scripts/android-emulator.py adb TOKEN shell am start -n com.timebox.android/.ui.assistant.AssistantTaskReviewActivity --ez review_details true
```

The fixture is for visual inspection. It does not persist review actions, and its
Open day callback is intentionally inert. Production uses the existing navigation
and correction paths.
