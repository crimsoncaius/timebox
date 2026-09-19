# Throwaway Trends comparison

Question: should a Task Type breakdown lead with ranked bars, proportions, or a daily time series?

Run from the repository root: `./scripts/prototype-trends.ps1` (optionally `-Variant B` or `-Variant C`). It builds the opt-in debug application, reserves a managed emulator, and leaves it for review. Release the printed reservation with the emulator helper once review is finished.

The prototype appears in the existing Android Chronicle Trends view. A bottom switcher cycles A (ranked bars), B (proportion wheel), and C (daily rhythm). An explicit Activity launch with `timebox://chronicle?variant=A` also selects a variant. This is the native equivalent of the UI prototype's URL variant switcher.

All variants share generated sample Actual time for the 90 days ending on the launch date in Asia/Singapore. The headline shows recorded time only; the elapsed-range comparison was removed after review. They never write records. Day, Week, Month, previous/next, custom dates, hierarchy expansion, and duration-to-contributing-days inspection are interactive. The current range and expanded groups appear in the prototype state footer.

Unconfirmed assumptions for review: Monday week start; initial current week; largest duration first; all percentages use the range total; zero-activity types omitted. Calendar drill-through is represented only by a contributing-days dialog. Running activity, persistence across app restarts, and arbitrary-depth hierarchy are not being tested here. The sample hierarchy has two levels.

The app shell and Calendar retain their normal behavior; Calendar still depends on the configured backend. The sample records are generated at launch using the date in Asia/Singapore.

Verdict: A (ranked bars), selected by the user. The follow-up adds ranked bars to expanded child Task Types, including directly assigned parent time. Child bars use the same range-total denominator, left edge, and full track width as parent bars. They are thinner; only their labels are indented. Keep this comparison on `codex/prototype-10-trends`; it is not production implementation.
