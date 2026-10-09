# GLM frozen-policy rerun with Phoenix

Batch `glm-compiled-2026-10-09-a` completed 39 real interactions with
`z-ai/glm-5.3-flash`, dataset-v4 and the unchanged compiled prompt
`8af41f713a831e322573fb5a5df9132676ba50452b4f8ae0225c73929e3ee032`.
There was no retraining, proposer call or held-out evaluation. GLM's reasoning
setting was left unset; the Luna-specific override was not applied.

| Set | Pass | Fail | Median total seconds | Maximum seconds |
| --- | ---: | ---: | ---: | ---: |
| Validation | 29/30 | 1 | 5.12 | 52.89 |
| Published regressions, three repetitions each | 8/9 | 1 | 8.64 | 13.27 |

Both failures contain correct factual text but omit the presentation header:

- `plan-free-gap-03`: correctly says 16:00–16:30 is unscheduled; parser rejects the
  plain-text response with a JSON parsing error.
- Third `18-check-subtask` repetition: correctly lists one checked and two unchecked
  children; parser rejects it with `Missing presentation header`.

All three Work breakdowns and rename regressions pass. Every interaction leaves
domain data unchanged. The two rejected responses earn zero regardless of the
correct raw text. Issue #315 remains open.

Phoenix's `timebox-presentation-experiments` project contains all 39 case roots;
their trace IDs match local evidence exactly and their grades total 37 pass/2 fail.
Model and tool spans are correlated below those roots. Filter the `experiment.batch`
attribute by the batch name above. Phoenix remains running for review.

There were 88 API requests. Reported cost is US$0.0464995820322; two interrupted
streams have unknown costs and retain a combined US$0.66479546368 reservation.
The reserve is not a reported charge. All amounts use the existing shared ledger
and remain within the approved ceiling.

Artifacts: `artifacts/presentation-experiment/glm-compiled-2026-10-09-a/` contains
the manifest, pricing, native/model events and summary. Console log:
`artifacts/presentation-glm-traced-console.log`. Production defaults are unchanged.

From `backend/`, with the local trace endpoint configured:

```powershell
uv run python -m scripts.presentation_luna NEW-BATCH-NAME --model z-ai/glm-5.3-flash --trace
```
