# Frozen compiled prompt: GPT-6 Luna comparison

**Grader update:** Regrading these same responses without new calls gives 23/30
validation and 3/9 regressions. See [grader corrections](assistant-presentation-grader-2026-10-08.md).
The original frozen-metric results below are retained for provenance.

The user requested a model-only rerun without training. Batch
`luna-compiled-2026-10-08-a` completed 30 validation cases and the three published
regressions repeated three times. No training, instruction generation, scorer
changes or held-out evaluation occurred. Production defaults remain unchanged.

The exact saved prompt has SHA-256
`8af41f713a831e322573fb5a5df9132676ba50452b4f8ae0225c73929e3ee032`.
The runner checks this before making calls. Dataset-v3, native tools, streaming,
parser and lifecycle checks match the GLM experiment.

## Configuration

- OpenRouter model: `openai/gpt-6-luna`; all 96 returned calls report that model.
- Reasoning effort: `none`, required for function calling on the existing Chat
  Completions interface according to the [official Luna model documentation](https://developers.openai.com/api/docs/models/gpt-6-luna).
- Maximum output: 4,096 tokens; no SDK retries. Provider selection requires support
  for requested parameters and applies verified maximum price limits.
- The pricing reservation includes all advertised long-context token tiers and
  cache-write prices. Provider-hosted web-search fees are excluded because this
  runner only uses local function tools, never hosted search.
- Shared total ceiling US$200; this batch ceiling US$10. No production model swap.

## Results under the unchanged metric

| Compiled policy model | Validation passes | Repeated regression passes |
| --- | ---: | ---: |
| GLM-5.3 Flash, earlier search-selected run | 29/30 | 8/9 |
| GPT-6 Luna, frozen-policy rerun | 17/30 | 1/9 |

Luna validation: 17 pass, 7 fail, 6 unresolved. Regressions: 1 pass, 3 fail,
5 unresolved. Validation median total time is 4.39 seconds; regression median is
8.05 seconds. One validation interaction produced no visible answer. All 39
interactions left domain state unchanged; no confirmation was executed.

The headline score understates some correct answers, but the operational failures
are substantial:

- Six validation unresolved answers correctly describe the free gap or revised
  appointment time. The frozen regexes miss phrases such as “no saved block”,
  “your plan is clear” and “six to seven this evening”.
- Five subtask-change validation cases cannot create a proposal because `read_tasks`
  rejects Luna's arguments. It includes fields belonging to other modes, even when
  their values are null: `Fields do not apply to the selected mode`. Repeated calls
  retain the invalid fields. The model reports lookup failure instead of completing
  the requested operation.
- Two validation card cases fail: one chooses an unsuitable combined snapshot
  instead of the requested lane cards; another repeats the same snapshot ID twice.
  The latter is rejected with `Invalid or unknown snapshot selection`.
- All three rename regressions fail due to the task lookup argument problem.
  All three subtask-display regressions also fail to retrieve the task; the metric
  labels their missing factual states unresolved. These are real unfulfilled
  requests, unlike the wording-only unresolved cases.
- The Work breakdown is numerically correct in all three repetitions. One earns
  credit with a grounded card; two are unresolved because the duration checker
  misses their prose phrasing. All three include the presentation header.

There were **zero missing-header errors**, but the duplicated snapshot selection
still caused a presentation-parser failure. A plain model swap therefore does not
provide a reliable overall fix. The selected GLM result is search-biased and the
comparison is one small run; this is not a general model-quality ranking.

## Cost, evidence and reproduction

Reported cost: **US$0.02098354005**, all 96 requests accounted for, no new unknown
reservations. Reported usage: 524,469 input tokens and 6,182 output tokens.
Existing reservations from earlier batches remain unchanged.

Local evidence: `artifacts/presentation-experiment/luna-compiled-2026-10-08-a/`
contains the source/fixture/policy manifest, pricing snapshot, full native/model
events and summary. Console: `artifacts/presentation-luna-console.log`.

From `backend/`, use a fresh batch name:

```powershell
uv run python -m scripts.presentation_luna NEW-BATCH-NAME
```

The runner imports no DSPy optimizer and loads no training or held-out cases.
Accounting tests: 4 passed, including long-context pricing tiers and refusal to
ignore hosted-search pricing without the explicit token-only mode. Runner lint
passes. The saved compiled prompt was not edited. Issue #315 remains open.
