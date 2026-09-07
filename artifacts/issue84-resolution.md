Implemented in the current working tree on web and Android (phone and tablet task menus). The destination picker includes existing Projects and Admin, requires an explicit Move, and supports cancellation. Only project_id is patched; the saved response updates the task lists. Failed saves retain the saved assignment and display feedback.

Existing lifecycle rules remain: completed Tasks must be reopened, Subtasks inherit their parent's Project, and a Task Occurrence move does not edit the Recurring Task Series.

Validation:
- Web: 37 Battle Plan tests passed; production build and targeted ESLint passed.
- Backend: 14 Battle Plan API tests passed, including Admin → Project → Project → Admin, Subtask preservation, unchanged Planned/Actual Block identities, and an invalid destination leaving the saved assignment intact.
- Android: testDebugUnitTest and debug build/install passed; the new picker instrumentation test passed on Pixel_9a.
- Live browser: verified cancellation, failed save and retry, all move directions, source-list removal, and persistence after reload. Temporary verification data removed.
- Updated web and Android instances launched and left with the Move to project picker open for review.

Closing as completed. Changes are in the local working tree; existing unrelated changes were preserved.
