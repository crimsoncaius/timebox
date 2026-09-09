# Issue #112: Android inline Project creation

Historical design decisions accepted during the grilling session on 2026-09-09.

Superseded by [Android Project bottom sheet](project-bottom-sheet.md), confirmed later on 2026-09-09. The current design uses bottom sheets for both creation and editing; the inline input and stable-menu requirements below no longer apply.

## Scope

Replace Android's navigation to a separate creation screen with inline Project creation in the Project navigation menu. Web behavior is outside this change. Existing Project editing is outside this change.

## Entry and draft

- Tapping **New project** replaces that action with a compact name input and confirm/cancel controls.
- Focus the input and open the keyboard on explicit entry into creation.
- Keep the menu open and the input visible above the keyboard, preserving the surrounding Project list styling.
- Preserve an unfinished name while the app stays open, including when the menu closes or another Project is selected.
- Reopening the menu displays the draft without automatically opening the keyboard.
- Explicit Cancel discards the draft and restores **New project**.
- Losing focus never submits the draft.

## Stable menu geometry

- Entering inline creation must not change the menu's outer size or position.
- The name field and confirm/cancel controls occupy the same fixed-height row as **New project**; neighboring rows stay in place.
- Reserve a small message area within that row for validation errors and saving status. Messages must not expand the row or menu, and failures retain the draft.
- The keyboard may reduce the menu height only as needed to stay above it. Keep the top anchored and scroll the Project list within the available space.
- Cancel restores **New project** in the same row without changing the menu height, apart from space becoming available when the keyboard closes.

## Keyboard and dismissal

- Back first hides the keyboard. A subsequent Back closes the menu while preserving the draft.
- Confirm and keyboard Done both submit.

## Validation and request

- Keep existing API naming rules: trim surrounding whitespace, require 1–200 characters, and reject case-insensitive duplicates.
- Disable submission for a blank name.
- While saving, keep the menu open, show progress, and temporarily disable editing, repeat submission, Cancel, and menu dismissal.
- On failure, display an inline error and retain the name for correction or retry.

## Success

- Add the new Project to the list using the existing append order.
- Select the new Project and display its task list within the existing layout, without a separate creation-page transition.
- Hide the keyboard, close the menu, clear the submitted draft, and restore **New project** for the next opening.

## Review scenarios

- Create a Project using either confirm or keyboard Done; verify one Project is added and selected.
- Cancel a draft; verify no Project is created and the action returns.
- Close and reopen the menu, or select another Project, with a draft; verify the name survives without automatic keyboard reopening.
- Exercise Back with the keyboard visible and then hidden.
- Submit a duplicate name or encounter a request failure; verify an inline error and retained draft.
- During a pending request, verify progress, blocked dismissal, and no repeat submission.
- Verify the input and controls remain reachable with the Android keyboard visible.

## Documentation impact

This changes interaction behavior without changing the definition of Project in CONTEXT.md. It does not require an ADR and does not conflict with the existing Project domain decisions.
