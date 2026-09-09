# Android Project bottom sheet

Design confirmed on 2026-09-09. Supersedes the inline creation interaction in `issue-112-inline-project-creation.md`.

## Scope and entry

- Both creating and editing Projects use the same Android bottom sheet. Web behavior is unchanged.
- New project and each Project's overflow Edit action close the navigation menu and open the sheet.
- Creation shows New project and Create project; editing shows Edit project and Save changes. Both have a name field and Cancel.
- Delete remains in the Project overflow menu with its existing affected-task confirmation; it is absent from the sheet.
- Use a compact bottom sheet on phones and tablets, capped at 560dp wide.

## Keyboard and drafts

- Focus the name and open the keyboard on every explicit opening, including a resumed draft. Keep the input and primary action visible above it.
- Swipe down, tap outside, and Back dismiss without discarding. Back first hides the keyboard; a subsequent Back dismisses the sheet.
- Keep one creation draft and a separate edit draft per Project while the app stays open. Switching Projects must not overwrite another draft.
- Cancel discards only the current draft. Successful submission clears only the submitted draft. Successful Project deletion clears that Project's retained draft.
- Dismissal and loss of focus never submit. Process-restart draft persistence is outside this change.

## Validation and saving

- Primary action and keyboard Done submit. Blank names disable submission.
- Trim whitespace, require 1–200 Unicode characters, and retain existing case-insensitive duplicate rejection.
- During a request, show saving status and disable editing, Cancel, repeat submission, and dismissal.
- Failures display the complete error in the sheet and retain the name for correction or retry.
- Creation appends and selects the new Project. Renaming retains list order and the selected scope, updating its label when the edited Project is selected.
- Success hides the keyboard and closes the sheet, leaving the Tasks view visible.

## Verification

- Check separate creation and per-Project drafts, Cancel isolation, and clearing after success.
- Check name validation, request failure/retry, and pending request guards for both operations.
- Check menu-to-sheet transition, focus, keyboard Done, two-step Back, and action reachability with the keyboard open.
- Check editing has no Delete action and preserves the selected scope.
- Build and launch the updated application for review.

## Domain documentation

Project remains a named group of Battle Plan Tasks. No glossary change or ADR is needed for this reversible presentation change.
