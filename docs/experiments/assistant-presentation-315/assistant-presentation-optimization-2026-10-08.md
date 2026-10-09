# Assistant presentation: exploratory live MIPROv2 run

**Grader update:** Regrading the saved selected-policy responses gives 30/30
validation and 8/9 regressions. See [grader corrections](assistant-presentation-grader-2026-10-08.md).
The original search scores below are preserved; no new optimization occurred.

The user authorized a real optimization run within the existing US$200 total
ceiling. This is a development experiment, not release approval. The production
presentation prompt and parser are unchanged, and issue #315 remains open.

## Method and provenance

- DSPy 3.4.0 MIPROv2; native `z-ai/glm-5.3-flash` answering pipeline, including
  tools, streaming, selector parsing, proposal lifecycle and acknowledgement.
- The same live model generates instructions through a separately metered DSPy
  proposer. Answer calls retain the application's settings; proposer temperature
  is 1.0. Each request has a 4,096-output-token limit and no SDK retries.
- 60 training cases and 30 validation cases; three published regressions are
  compared separately. The 30 held-out cases were neither loaded nor executed.
- Three instruction candidates, four search trials plus a default-program
  evaluation, up to two bootstrapped examples, one worker, fixed seed 9. Each
  evaluation uses all 30 validation cases. This is a small search, not exhaustive.
- Controlled model scripts seed prior conversation history only. Every target
  answer and instruction proposal uses real paid API calls.
- Dataset-v3 broadens two validation wording checks discovered during baseline
  audit. Retained baseline responses are regraded without new model calls.
  Dataset-v1/v2 and original grades remain available; held-out bytes are unchanged.
- Batches `mipro-2026-10-08-a` and `-c` supplied the retained baselines. Batch `-a`
  was interrupted for the first wording correction; `-b` failed before making any
  requests; `-d` performs the completed search and regression comparison.

## Development validation

| Policy | Pass / 30 | Fail | Unresolved | Median total seconds | No visible output |
| --- | ---: | ---: | ---: | ---: | ---: |
| Current baseline | 23 | 7 | 0 | 6.45 | 5 |
| Manual revision baseline | 26 | 4 | 0 | 6.59 | 4 |
| Current, fresh default-program evaluation | 21 | 9 | 0 | 5.62 | 7 |
| Generated rewrite + two text examples | 17 | 13 | 0 | 7.03 | 8 |
| Current instructions + two text examples | 29 | 0 | 1 | 6.50 | 0 |
| Current instructions + one card example | 24 | 4 | 2 | 5.99 | 4 |
| Generated rewrite + one card example | 18 | 12 | 0 | 6.07 | 8 |

The strongest combination retains the existing instructions and adds two short
arithmetic examples with the selector first. Its unresolved validation answer
correctly says “6:00 to 7:00pm”; the frozen time-expression check misses this form.
Its official score remains 29/30, rather than silently changing the metric after
search. One passing response from each validation family was manually audited,
including proposals and fresh two-card snapshot selection.

A generated rewrite hard-codes the synthetic date and requires a read every turn.
That violates the intended generality of a presentation-only policy and would
disqualify that rewrite from deployment regardless of score. The search's preferred
policy avoids those additions. Two valid arithmetic bootstrap answers were also
excluded because the number-only checker rejects an extra blank line; this is a
known conservative scorer limitation, not a model failure.

Scores are search-selected, correlated across five variants per family, and based
on one synthetic account. The current prompt's 23/30 versus 21/30 demonstrates run
variation. These results do not establish generalization or guarantee compliance.

Available response usage for the 30-case current/manual/selected-policy runs is
respectively 608,756 / 636,847 / 647,722 input tokens and 10,595 / 10,837 / 10,207
output tokens. Median per-interaction input counts are 20,884.5 / 21,041.5 /
21,472.5; median output counts are 353.5 / 374 / 292.5. These include multi-call
interactions and provider-reported cached input; interrupted responses may lack
usage. First-visible medians among responses that became visible are 6.26 / 6.08 /
5.29 seconds; missing-visible counts remain in the table above.

## Repeated published regressions

Each of the three known regressions runs three times per frozen candidate, with
candidate order rotated between repetitions. These are additional measurements
of known cases, not held-out evidence.

| Policy | Pass / 9 | Fail | Unresolved | Median total seconds | No visible output |
| --- | ---: | ---: | ---: | ---: | ---: |
| Current | 4 | 4 | 1 | 6.25 | 3 |
| Manual revision | 5 | 3 | 1 | 5.94 | 3 |
| Compiled | 8 | 1 | 0 | 5.33 | 1 |

The compiled policy passes all three rename attempts and all three subtask-state
attempts. It passes two Work breakdown attempts, but the third emits correct
9/6/3-hour figures without a presentation header. The native parser rejects it:
`ValueError: Missing presentation header`. The proposal/answer contract therefore
still fails occasionally, and #315 must remain open.

The current and manual unresolved answers also contain correct 9/6/3-hour figures;
the duration checker misses “6 hours were Coding” / “6h was Coding”. Their recorded
scores stay unchanged. All 242 retained interactions, including imported baselines,
bootstrap attempts, search evaluations and regression repetitions, report unchanged
domain state. No confirmation was executed.

## Cost and saved candidate

| Batch | Requests | Reported cost (USD) | Unknown-cost reserve (USD) |
| --- | ---: | ---: | ---: |
| Optimization baseline `-a` | 37 | 0.01853420563665 | 0.322285801472 |
| Optimization baseline `-c` | 99 | 0.05272922974077 | 0 |
| Search + regression comparison `-d` | 429 | 0.23920220571984 | 0.322285801472 |
| Optimization total | 565 | 0.31046564109726 | 0.644571602944 |

Including the earlier six-case pilot, reported cost is **US$0.32394360999726**.
Reported cost plus remaining conservative reservations is **US$0.96851521294126**,
well below the US$200 ceiling. Reservations are not reported charges: one belongs
to the stopped baseline request, the other to a stream interrupted by a parser
failure. Their actual costs are unknown and remain reserved. The completed run
released its lock; no further paid requests are running.

The exact export is saved as a reviewable study artifact at
`backend/studies/presentation/compiled-exploratory-2026-10-08.json`, without changing
the runtime default. Its rendered prompt SHA-256 is
`8af41f713a831e322573fb5a5df9132676ba50452b4f8ae0225c73929e3ee032`.
It contains the original instructions and the two successful arithmetic examples,
with no synthetic date or snapshot IDs. DSPy is not required to consume the
exported prompt string.

Before a release decision, improve the scorer's semantic time/duration handling
against retained development evidence, constrain the proposer against fixture
constants and unrelated tool-policy changes, and agree release thresholds. Then
freeze the candidate and metric before a separate untouched held-out comparison.
This completed exploratory run is evidence of improvement, not a finished fix.

## Evidence and validation

Full prompts, source hashes, pricing, model inputs/outputs, native events, grades,
timings and the exported policy are retained locally under the ignored directory
`artifacts/presentation-experiment/mipro-2026-10-08-d/`. The console search trace is
`artifacts/presentation-optimize-search-d.log`. The shared durable ledger is
`artifacts/presentation-experiment/budget.json`.

Runner/accounting/adapter tests: 16 passed. Dataset checks: 103 passed, including
controlled development replays and structural held-out integrity checks. Runner
lint and `git diff --check` pass. The local review API remains available on port
8001 with the unchanged production prompt. No PR, merge or deployment was made.
