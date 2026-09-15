# Recurring Task Series list cards (#182)

Android-only. A discarded web Recurring-list prototype is not a source.

## Decisions

- **Occupancy: Flush compact.** Series use the compact Battle Plan card shell and two-line title, without a completion column. Recorded after round 1.
- **Scan: equal chips.** Task type, cadence, and next Task Occurrence or quota period are peer facts. Schedule and quota stay one family; mode is in the third chip copy. Quota Tracker progress stays off the series row. Recorded after round 2.
- **Trailing: tap opens details; keep overflow for later Delete.** Pause, resume, and end stay on series details. Overflow is reserved for deleting ended series, not for opening the card. Recorded after round 3.
- **Lifecycle: status chip.** Paused and ended series keep a status chip on the card. Active has no chip; the Active/Paused/Ended tabs still filter the list. Recorded after round 4.

## Production

The Recurring list uses the locked card. Overflow Delete on ended series uses the existing confirm dialog and API write. Pause, resume, and end remain on series details. Creating a series pops back to this list; it does not open series details.

## Provisional

- Series are not completable. Flush has no leading control.
- Quota progress stays on generated Battle Plan Quota Trackers, not the series row.
- Recurring-task details and management stay out of scope (#95, #115).
