# Issue 168 placement prototype

Throwaway branch: `codex/prototype-168-task-type-recommendations`.

Question: where should an optional Task Type Recommendation appear when Android edits the name in a separate field sheet?

From `frontend`, run `npm run prototype:168`, then open:

http://127.0.0.1:12034/battle-plan?prototype=task-type-168&variant=A

- A: recommend in the focused name editor, with acceptance staged until Save.
- B: recommend beside Task Type in the details after saving the name.
- C: combine name and Task Type in a single editor, with acceptance staged until Save.

The floating switcher and left/right arrow keys change variants. Arrow keys remain normal inside editable fields. Switching variants resets fixture state; the variant is preserved in the URL.

This is a browser facsimile of the Android surface on the existing Battle Plan route, using its React/Vite task runner. It bypasses application providers while active so no real data is fetched or mutated. It is development-only. Native keyboard behavior, exact native rendering, production picker behavior, and real Jev/Phoenix connections are outside this prototype.

Try changing the name to Practise guitar, Morning run, or Buy groceries. Wait 500 ms to see a simulated recommendation. Accept, dismiss, save, or cancel; inspect state in the control panel. Use the response selector to exercise low confidence, no match, unavailability, or the candidate limit. The Actual Block fixture can be linked to suppress independent classification.

Validation: production build passed. Browser checks exercised acceptance and save in A, delayed arrival after Save in B, and low-confidence manual selection in C.

Verdict: awaiting user comparison. No placement is selected or promoted into production. The agreed behavior and observability requirements are in `docs/specs/issue-168-task-type-recommendations.md`.
