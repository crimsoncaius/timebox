# Issue 75: Project ordering review

Implemented locally on 2026-09-05. Existing uncommitted work was preserved; no commit, push, or issue closure was performed.

## Behavior

- Web Project rows have folder icons, drag handles, and a More actions disclosure containing Edit, Move up, and Move down. Todoist's signed-in web sidebar was inspected as the visual reference.
- Dragging is restricted to the Project list. Existing fixed navigation and task membership are unaffected.
- Android phone navigation supports long-press dragging directly in the primary Project menu. The held row follows the finger, neighboring rows move aside, and long lists scroll near the edge. Project names still navigate; More actions contains accessible Move up/down commands. The wider tablet layout retains its separate reorder dialog.
- Both clients consume the backend's saved order. New Projects append; renaming does not reorder.
- Reordering submits every Project ID exactly once. Invalid or stale membership is rejected before writes. Persistence failures are reported and the prior list remains visible.
- Migration 019 initializes existing Projects in the previous case-insensitive alphabetical order with an ID tie-breaker.

## Validation

- Backend: 18 tests passed across Project ordering and Battle Plan API tests, including migration ordering, invalid-request atomicity, rename, append, and task membership preservation.
- Web: production build and focused ESLint checks passed. 33 tests passed across Sidebar, BattlePlanPage, and ProjectEditor.
- Live web: pointer drag saved successfully and survived reload; Tab/Enter through the Project actions moved a Project successfully.
- Android: compiled successfully. Both ProjectOrderDialog instrumentation tests passed on Pixel_9a, covering long-press drag and accessible moves/boundaries.
- Android inline follow-up: all three ProjectNavigationList instrumentation tests passed on Pixel_9a. The actual Battle Plan menu accepts drag reordering without another dialog or accidental navigation, stays open after a move, and still navigates when a Project name is tapped. Additional checks cover action-menu boundaries and canceled drags.
- Wider Android unit suite: 145 passed, 1 failed in `DayWorkModeViewModelTest.resizing Actual Block persists Actual timestamps`. The failure concerns Actual Block resizing outside the Project-ordering changes; it was not modified in this task.

## Review environment

- Web: http://127.0.0.1:5176/battle-plan
- API: http://127.0.0.1:8001
- Android: Pixel_9a emulator, updated application installed.
- Local PostgreSQL: port 54330.
- Alembic current and head: `019_project_order` (schema invariant satisfied).
- Migration verification: identities and mutable record counts preserved.
- Verified pre-migration backup: `C:\Users\Caius\AppData\Local\Temp\timebox-backups\timebox-20260905-194323.dump`.
- Backup size: 45,335 bytes.
- Backup SHA-256: `6161FBCB164BDF836EA1C3EA61A132A74CD1D4CFFA381907782D0AFA5C9B9D6C`.
- Subsequent launches required no migration or additional backup.
- Three local sample Projects were added for review: Review - Design, Review - Development, and Review - Launch. Their descriptions identify them as local review samples for this issue.
