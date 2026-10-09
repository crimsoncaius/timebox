# Assistant presentation reliability — issue #315

Design confirmed; implementation authorized on 2026-10-08. Accepted decisions below define the experiment, not a production rollout. Related issue: https://github.com/crimsoncaius/timebox/issues/315.

## Accepted decisions

On 2026-10-08, the user accepted:

1. Run a bounded prompt-optimization experiment for #315. Reuse evaluation components where useful, but do not commit the entire Assistant to DSPy without evidence.
2. Preserve existing tools, presentation protocol, parser, and confirmation rules initially. Optimize only presentation instructions and examples. Jev and a separate card-selection tool remain alternatives for later evaluation.
3. Label Assistant Card expectations as required, optional, or unwanted. Allow multiple correct presentations where appropriate; do not mistake a single stylistic preference for correctness. All selected cards must remain relevant and grounded in eligible reads.
4. A substantial reduction in failures is progress, not automatic resolution. Keep #315 open if presentation failures still lose valid answers. Establish a baseline before agreeing a release threshold; prompt compilation is not a compliance guarantee.

The user subsequently accepted:

5. Evaluate each live candidate through complete interactions using real tools, streaming translation, parser, and response lifecycle. Retain separate deterministic replay tests for chunk boundaries and interruption behavior.
6. Cover ordinary answers; required, optional, and unwanted Assistant Cards; multiple available cards; Task and Tracking Proposals with and without preceding reads; and targeted history cases. Include unavailable reads and stale historical data. Broad multi-turn workflow exploration remains out of scope.
7. Initially optimize binary complete-interaction success and retain separate failure diagnostics. Cancellation and proposal safety remain mandatory release gates. Reconsider partial credit only if baseline evidence shows insufficient optimization signal.
8. Preserve the three published failures as permanent regression cases, not unseen evidence. Separate training, validation, and untouched test sets by related scenario/template families, including paraphrases. Once test failures inform revisions, that test set is no longer untouched; reserve fresh cases for the next decision.
9. Keep the production answering model, generation settings, tool limits, and retry policy fixed across comparisons. Record exact configurations. The candidate-proposing model can be configured separately.
10. Compare the current prompt, a small manually improved instruction/example baseline, and the MIPROv2 result under equivalent conditions. Report correctness, first-visible-output latency, total duration, and token usage; retain failures.
11. Build a small representative fixture set and review its expected outcomes before expansion. Validate the scorer against deliberately incorrect answers, incorrect cards, leaked control syntax, and invalid proposal states.

12. The total initial paid-run ceiling is US$200, covering candidate generation, compilation, baseline comparisons, and held-out evaluation. This is a ceiling, not a spending target. Start small, track usage, and stop before exceeding it. Pause paid runs if pricing or usage cannot be bounded reliably; increases require user approval. Execution was authorized after the final shared-understanding confirmation.
13. Prove the DSPy bridge on text-only, read-plus-card, and read-plus-proposal scenarios before scaling. Verify ordinary and forced-final prompt propagation, native parser failure scoring, usable bootstrap demonstrations, and equivalent model inputs after export/reload. Reassess if substantial production restructuring is required.
14. Initially use programmatically checkable facts/outcomes with equivalent wording accepted, plus manual audits of passes and failures. Mark uncertain assessments unresolved rather than automatically passing them. Introduce an AI judge only if unresolved cases materially constrain the experiment.
15. After the small fixture review, target 120 cases: 60 training, 30 validation, and 30 held-out, separated by scenario/template family, plus the three permanent regressions. Cover the accepted scenario categories across splits. This is an initial experiment, not evidence of near-perfect reliability.
16. Unresolved grades receive no success credit but are reported separately from confirmed failures. Audit unresolved training/validation assessments before compilation to avoid rewarding checker-specific wording. Freeze the scorer before held-out testing.
17. Initially use the production model for both answering and candidate proposal, recording settings separately. Revisit the proposer explicitly if generation or DSPy compatibility fails feasibility checks.
18. Evaluate each frozen candidate three times per held-out and regression case with fresh fixture state. Interleave candidate runs and retain every attempt. Separate compilation validation calls from final comparison results. Revisit repetitions if the pilot cost estimate exceeds the accepted ceiling.
19. Render compact, explicitly labeled demonstrations inside the presentation instructions, separate from real conversation history. Include only context needed to demonstrate the format; exclude evaluation labels, diagnostics, and fixture identifiers. Example snapshot IDs must never become eligible runtime evidence. Use the same renderer during optimization and production export.
20. After feasibility checks, fixture review, and baseline runs, review results with the user and agree correctness, latency, and token-cost limits before compilation and held-out comparison. Keep held-out cases untouched until the final comparison. A failed experiment leaves #315 open; Jev or a changed presentation contract requires a separate decision.

These are reversible experiment choices, so this document records them without introducing an architecture decision record or implementation terms into the domain glossary in CONTEXT.md.

## Implementation facts informing the discussion

- The production prompt is currently monolithic. A narrowly replaceable presentation fragment needs an explicit composition seam covering ordinary and forced-final calls.
- Production uses raw model streaming. Evaluation must exercise actual event translation and parser boundaries; a DSPy structured-output adapter alone would test a different interface.
- Completed, acknowledged history reconstructs presentation selectors. It is not a byte-for-byte replay of original model messages.
- Existing Tracking Proposal paths can accept plain text without a selector under specific conditions. Metrics must check the actual protocol, not blindly require a literal header everywhere.
- Existing tests provide isolated SQLite fixtures and real graph/tool/HTTP replay coverage. No reusable live study harness was found in the tracked working tree; the issue's separate study branch and local artifacts still need inspection before reuse decisions.
- MIPROv2 optimizes DSPy predictor instructions and demonstrations, not arbitrary application prompt strings. Native-agent evaluation therefore requires a custom bridge. Bootstrapping demonstrations also requires usable predictor traces; exposing an unused predictor's instructions alone is insufficient. The proposed bridge and production export parity must be proven against a pinned DSPy version before a large search.

## Execution stages and agreed checkpoints

1. Following final confirmation, implement and verify the narrow DSPy bridge with controlled model outputs before paid optimization. Inspect the separate study branch/artifacts for reusable fixtures and preserve original evidence. Pin the DSPy version and prove instruction/demo propagation, bootstrap traces, and export parity.
2. Build a small representative fixture set and scorer, including deliberately incorrect results. Review expected outcomes with the user before expanding to the agreed split. Specify checkable required facts and presentation expectations per case.
3. Estimate cost, run development baselines within the US$200 total ceiling, and review findings with the user. Set release criteria and any latency/token limits before compilation. Do not use held-out cases to establish these thresholds.
4. Compile the presentation fragment, freeze candidates and scorer, then run the agreed repeated held-out/regression comparison. Preserve all failures and unresolved grades; separate these results from the original study and search-time validation.
5. Report outcomes and retain the winning candidate only if justified by evidence and agreed criteria. Application changes require validation and a running review instance under repository instructions. No PR or merge without explicit user instruction.

If the bridge requires substantial production restructuring, cost cannot be bounded, or the proposer is incompatible, return to the agreed decision checkpoint rather than silently changing scope. This experiment does not authorize parser recovery, Jev integration, or changes to confirmation rules.

## Implementation checkpoint — 2026-10-08

Controlled-output feasibility is implemented. DSPy 3.4.0 with its Optuna extra is
optional; production has only a presentation-fragment injection seam with the
default wording preserved. Tests exercise native HTTP lifecycle, real reads and
Task Proposals, bootstrap traces, MIPRO candidate/search wiring, export equivalence,
normal/forced-final propagation, and the three captured original parser failures.

The focused Assistant suites pass (112 tests). No paid calls were made. This is
integration evidence, not evidence that a compiled prompt improves reliability.
The five pilot expectations were reviewed and the user authorized expansion.
`backend/studies/presentation/dataset-v1/` now contains 60 training, 30 validation
and 30 held-out cases across 24 disjoint intent/setup families, plus the three
published regressions. Each family has five related variants; these are not 120
independent samples. The immutable manifest records file and fixture-source hashes.

Development checks execute controlled outputs against the real API, tools and
lifecycle, including seeded acknowledged/unacknowledged history, an intervening
plan edit, tracking proposals, partial/unavailable reads and multiple cards.
Held-out cases have structural validation only; no candidate has run against them.
The expanded focused suites pass: 215 tests, including all 90 development replays.
Duration and child-state checks now validate the two factual regressions. Unfamiliar
phrasing remains unresolved and earns no success credit; manual audits remain
necessary for unsolicited claims and before freezing the scorer.

The user subsequently authorized a real run. A six-case current-prompt development
pilot made 12 real model calls at US$0.0134779689 reported cost: four interactions
completed, and two reproduced invalid presentation prefixes. Durable reservations,
shared US$200 accounting and full transcripts are implemented. See
`docs/experiments/assistant-presentation-315/assistant-presentation-live-2026-10-08.md`.

The user's subsequent request to run optimization authorizes an exploratory
training/validation search within the existing budget. The audited validation
baseline is 23/30 current and 26/30 manually revised, using dataset-v3 wording
checks and retained real responses. MIPROv2 compilation runs independently of
release approval; correctness/latency/token release criteria and untouched held-out
comparison remain pending. Production behavior is unchanged. Issue #315 remains
open.

The exploratory MIPROv2 run completed: the original presentation instructions plus
two bootstrapped text examples scored 29/30 validation and 8/9 repeated known
regressions. One Work answer still omitted its header. The saved study export is
not active in production. Full results, costs and scorer limitations are in
`docs/experiments/assistant-presentation-315/assistant-presentation-optimization-2026-10-08.md`.

## Approved text-only selector amendment (2026-10-09)

The user approved accepting ordinary final-answer text without a presentation
selector. This supersedes the earlier prompt-only/parser-unchanged scope for this
implementation. Missing selectors mean no cards; explicit selectors remain
validated and a leading none-selector remains supported. Malformed or misplaced
selectors are not repaired. Card-required requests still fail evaluation if their
answer contains no card. Proposal acknowledgement and confirmation rules remain.

The production prompt describes the optional text-only selector without requiring
its omission. Historical compiled policies and run evidence remain unchanged.
Both captured failures from `glm-compiled-2026-10-09-a` now complete and pass when
their original text is replayed through the native API. No paid model calls were
made for these replays. See
`docs/experiments/assistant-presentation-315/assistant-optional-selector-2026-10-09.md` for validation and scope.

## Runtime decision after experiment review

The user elected to retain DSPy in production to avoid divergence between the
optimization and application harnesses. This supersedes the earlier offline-only
proposal above. One shared native DSPy module renders instructions/demonstrations
and invokes the production agent executor. Evaluation adds isolated fixtures and
grading around that executor. Saved policies use native DSPy JSON state; runtime
loads the reviewed state with `ASSISTANT_POLICY_PATH`, otherwise using the current
built-in instructions. No historical compiled candidate is activated implicitly.
The current dataset is `backend/studies/presentation/dataset`; historical revisions
and results are indexed in `docs/experiments/assistant-presentation-315/README.md`.
