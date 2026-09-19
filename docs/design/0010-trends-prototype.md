# Throwaway Trends comparison

Question: should a Task Type breakdown lead with ranked bars, proportions, or a daily time series?

Run from the repository root: `./scripts/prototype-trends.ps1` (optionally `-Variant B` or `-Variant C`). It builds the opt-in debug application, reserves a managed emulator, and leaves it for review. Release the printed reservation with the emulator helper once review is finished.

The prototype appears in the existing Android Chronicle Trends view. A bottom switcher cycles A (ranked bars), B (proportion wheel), and C (daily rhythm). An explicit Activity launch with `timebox://chronicle?variant=A` also selects a variant. This is the native equivalent of the UI prototype's URL variant switcher.

All variants share deterministic sample Actual time from 22 June through 19 September 2026. They never write records. Day, Week, Month, previous/next, custom dates, hierarchy expansion, and duration-to-contributing-days inspection are interactive. The current range and expanded groups appear in the prototype state footer.

Unconfirmed assumptions for review: Monday week start; initial current week; largest duration first; all percentages use the range total; zero-activity types omitted. Calendar drill-through is represented only by a contributing-days dialog. Running activity, persistence across app restarts, and arbitrary-depth hierarchy are not being tested here. The sample hierarchy has two levels.

The app shell and Calendar retain their normal behavior; Calendar still depends on the configured backend. The prototype's sample date is fixed and is independent of the device date.

No variant has been selected yet. Keep this comparison on `codex/prototype-10-trends`; it is not production implementation.
