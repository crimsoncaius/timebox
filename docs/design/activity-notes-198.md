# Current Activity notes — progressive prototype (#198)

## Fixed behavior

- The initial scope is Android only. Web is deferred.
- A linked Current Activity edits that specific Battle Plan Task, Task Occurrence, or Session Task's Task Description.
- An unlinked Current Activity edits its running Actual Block's Supporting Note.
- Linkage silently determines the target. A linked activity never exposes or falls back to its Supporting Note.
- Completed linked Tasks remain editable while tracking continues. Archived and trashed linked Tasks are read-only.
- Task Occurrence and Session Task edits do not affect their series, quota, or siblings.
- Saving replaces the whole field; blank clears it.
- Both Task Description and Supporting Note edits require connectivity for the initial Android scope. A failed save retains its draft.
- Existing last-successful-write behavior remains; this feature adds no merge or version-conflict UI.

## Round 1: rough connected experience

Question: Does an explicit Notes action and Save/Cancel bottom sheet fit both expanded Activity Tracking and Focus Mode on Android?

- Expanded Activity Tracking places Notes on the same action row as Switch, Focus, and Stop.
- Focus Mode exposes a full-width Notes action immediately above Switch activity.
- Both open the same bottom sheet. It names the resolved field (`Task Description` or `Supporting Note`) and explains replacement scope.
- The editor writes through the online Task API or an acknowledged, non-queued Activity command. Save remains disabled while Activity Tracking reports Offline.
- Archived and trashed linked Tasks open a read-only view.
- The sheet keeps its draft and shows an inline error when a network write fails.

Recommendation: start with explicit Save/Cancel. It matches existing task and Actual Block editors, gives a clear recovery point for connectivity failures, and keeps activity switching semantics understandable. Continuous saving remains worth comparing only if explicit saving feels interruptive in the connected flow.

## Open decisions

- What happens to an unsaved draft when the Current Activity changes.

## Accepted UI direction

- Use explicit Save/Cancel rather than continuous saving.
- In expanded Activity Tracking, place Notes on the same action row as Switch activity, Focus, and Stop.
- In Focus Mode, place a full-width Notes action immediately above Switch activity.
- Use the shared bottom sheet for both Task Description and Supporting Note.

## Coverage still needed

- Review linked, unlinked, completed, archived, and trashed targets with representative Android data.
- Review switching, Focus entry/exit, offline save gating, and failed-save draft retention.
- Revisit web only when the platform scope expands.

## Validation

- Android debug build and `ActivityRepositoryTest` pass.
- The connected instrumentation APKs built, but the emulator instrumentation process crashed before discovering tests (0 tests).
- On emulator-5580, an unlinked Supporting Note was saved online, the sheet closed only after acknowledgement, and reopening from ordinary Activity Tracking and Focus Mode showed the saved value.
