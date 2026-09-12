# Issue 160: Android Focus Mode visual prototype

Throwaway browser surrogate for the native Android Focus surface. No Android or web application code changes. The question: which composition restores the spacious legacy Work Mode feel while keeping Focus separate from Activity Tracking?

Run from the repository root:

```sh
python android/prototypes/serve-focus-160.py
```

Open http://127.0.0.1:12008/prototypes/focus-160.html?variant=A. Use the floating arrows or keyboard left/right to compare A (editorial canvas), B (quiet clock), C (task companion). The HTML can also be opened directly in a browser. Fonts use the existing bundled Android resources.

Platform confirmed by user: Android only. Final direction: awaiting user review. A is the initial recommendation for continuity with legacy Work Mode, not an accepted decision.

All state is temporary; elapsed time is a frozen sample. Task completion, checkboxes, activity changes, and leaving/re-entering Focus are simulated. The state inspector exposes tracking separately from Focus visibility. Scenario selection covers unnamed activity, check-in, plan mismatch, offline, and long content. Light and dark palettes are available for every composition.

This evaluates composition only. Native Android back handling, wake policy, keyboard insets, actual sync, task linkage after an activity switch, and the native modal Inactivity Prompt are not implemented or validated here. The prompt is displayed inline to compare its visual weight. Switch timing/type selection and linked Actual Block totals remain implementation requirements in the real surface. These static prototypes never ship through a production build, and must remain on the throwaway branch.

References: b4d985d54f9bb2ee0daf8afc2cc579999ca80f60 and 94b790d6c616579ace8df6a4aeba270fe6e5d622. Existing Android Manrope/Inter resources inform typography.

Review performed: inspected A/B/C screenshots in browser; exercised Task completion/undo, scenario selection, exit/re-entry, keyboard wrap, and URL reload. Confirmed exit leaves simulated tracking enabled and exit remains visible in all scenarios. No production tests added for throwaway code.
