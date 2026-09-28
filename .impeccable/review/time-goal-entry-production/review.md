# C1 creation with bottom sheets — production review

User selected the underlined C1 sentence, requested bottom sheets, and authorized implementation on 28 September 2026. Production branch: `codex/time-goal-entry`; prototype comparison remains at `codex/prototype-time-goal-entry` / `4f02714f`.

Debug APK builds. All 388 Android unit tests pass. Two focused `TimeGoalCreationTest` instrumentation tests pass, covering invalid minutes, corrected duration, monthly default start and creation payload, sheet Back, retained values, and explicit discard. Existing backend behavior is unchanged; the full instrumentation suite has a documented failing baseline and was not rerun.

Manual real-app creation saved learning/reading at 120 minutes every two weeks from 28 September, with the backend confirming an end date of 11 October. Review uses isolated sample SQLite on port 12077 and a separate application ID. No shared backend records were modified.

Captures: phone (initial creation), duration, task-type, period, saved, dark-large, details-dark-large and duration-dark-large. Phone is 1080 × 2424; dark captures use font scale 1.3. All images were opened and inspected. The long path wraps fully, period details remain readable, and field sheets are opaque with a reachable Done action.

Independent native finish reviewer: **ship**, no material fixes. The selected sentence hierarchy, secondary counting rules, anchored Create and existing native sheet family are faithful. Independent documentation pass: no changes to the durable design system.

Limits: emulator only, no tablet/TalkBack/physical-device verification. The emulator offered a handwriting input method, so these captures do not establish full phone-keyboard behavior. A configuration transition retained the draft; the existing sheet briefly offered discard confirmation, which was dismissed with Keep editing.

Current review reservation and backend restart details are recorded in `docs/design/time-goals.md`.
