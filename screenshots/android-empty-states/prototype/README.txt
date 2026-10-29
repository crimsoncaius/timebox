THROWAWAY EMPTY-STATE PROTOTYPES

Question: which arrangement makes the current empty states understandable while
preserving Timebox's quiet Android visual style?

A: compact inline heading, explanation and local action (recommended starting point).
B: contained tonal panel with an explicit action region.
C: centered heading, explanation and action, with more visual prominence.

The HTML gallery contains 14 before/after pairs across the seven reviewed groups.
After panels are HTML recreations, not Android captures or production changes.
Inter and Manrope are copied from the app's bundled fonts. Existing header/dock
context is retained in the recreations. Google Calendar's before image comes from
the existing component fixture; the after removes its fixture-only wrapper.

Run from the repository root:
  python screenshots/android-empty-states/build-gallery.py
  python -m http.server 12115 --bind 127.0.0.1 --directory screenshots

Open http://127.0.0.1:12115/android-empty-states/?variant=A (or B/C).
Alternatively open index.html directly; all assets are local.

The bar and left/right arrow keys switch directions. Individual frames can be
reset. Actions open illustrative draft forms, prompts fill but do not send, sync
is simulated, and task-type creation is a preview. Nothing persists or calls an API.

All screens assume known empty content, not failed loading or active filters.
Habits assumes no eligible routines exist; historical empty weeks should retain
their grid, and an all-already-tracked case needs separate copy. The proposed
Create a routine handoff and exact form contents require production wiring if
selected. No design has been accepted yet; do not promote this code to production.
