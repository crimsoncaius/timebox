# Task Type recommendations inside the picker

Extends issue #168 on web and Android. The selected presentation is prototype A: a separate Suggested Task Type panel with Use and Dismiss, inside the picker above ordinary path results. The primary-source prototype and design discussion are preserved on `codex/prototype-jev-picker` in `docs/design/jev-picker-prototype.md`.

## Behavior

- One JEV request combines separately labelled current item name, user-entered picker text, and linked task name where present. No fixed input-priority rule. Omit blank fields.
- Task, occurrence and session editors supply their Task name; recurring editors supply the series name. Planned and independently editable Actual Blocks supply their Block Name and linked Task name. Starting/switching Activity Tracking supplies the new activity name, never the outgoing activity. Focus shares those controls.
- Opening predicts from available names before typing, including on classified work. A stored selected path is not user-entered query context. No names and no query means no request.
- Context changes invalidate displayed results immediately and debounce the next request for 500 ms. Late results cannot apply to another context or a closed picker.
- Dismissal lasts until an input changes or the picker closes and reopens. Acceptance closes the picker and uses its existing assignment/save flow. Hide a recommendation equal to the selected type.
- Preserve the 0.8 provider-confidence threshold, existing-type-only candidates, maximum 254 eligible candidates, no-match option, quiet failure behavior and backend-only credentials. Ordinary path ranking and manual creation remain independent.
- Descriptions, Supporting Notes, Projects and history are excluded. Actual Blocks linked to a Planned Block cannot independently accept recommendations. Existing classification propagation and Block independence remain unchanged.

This supersedes the name-change-only triggers and outside-picker placement in the original issue-168 spec. It does not change ADR-0005, ADR-0006 or ADR-0008.

## Validation and review

Backend recommendation tests: 14 passed. Focused web picker tests: 21 passed; the wider picker/block-form selection also passed before the final presentation adjustment. Production web build passed. Android debug APK and 322 unit tests passed.

Live JEV checks in an isolated SQLite review database verified opening from a task name, combined name/query requests, acceptance and saving, and one Planned Block request containing `Scales and arpeggios`, picker query `learning`, and linked task `Practice piano scales`. JEV returned `learning/music` inside the picker.

Web review runs on localhost port 12061 against the updated backend on port 12060. Review data is temporary. Fresh emulator creation initially failed due to disk space; failed reservations were released. The user then assigned an existing free review emulator. Installed the updated APK on emulator-5640 and verified live `learning/music` suggestions inside the Task Type picker both on opening and after entering `learning`. Captured and inspected the native screen. The device is retained in review state using reservation 7424c6e1578e48fd81fab718425bef6f. The full instrumentation suite was not run; no merge is authorized.
