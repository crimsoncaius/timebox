# Issue #315: first live presentation baseline

On 2026-10-08, six real interactions using the current production prompt and
`z-ai/glm-5.3-flash` reproduced two presentation failures. Four interactions
completed. This is a small diagnostic pilot, not a population reliability estimate.
No prompt optimization or held-out evaluation was performed.

| Case | Native result | End-to-end seconds | Finding |
| --- | --- | ---: | --- |
| Arithmetic only | Completed | 2.77 | Correct answer, valid leading selector. |
| Today's plan | Completed | 4.84 | Valid card-only selector; all four planned blocks shown. |
| Track Reading now | Completed | 4.31 | Correct unconfirmed Tracking Proposal; plain-text final accepted by the existing tracking-specific path. |
| Rename Weekly report | Completed | 5.52 | Correct pending title-change proposal, valid leading selector; no mutation. |
| Work breakdown | Interrupted | 8.47 | Correct 9h Work / 6h Coding / 3h Admin answer, but no presentation selector. |
| Export subtasks | Interrupted | 4.59 | Correct three subtask states, but the selector appeared at the end. |

Both failed interactions raised `ValueError: Missing presentation header`, delivered
no visible answer, and were not acknowledged. The subtask failure ended with
`{"presentation":"none"}` after its answer. The Work answer had no selector at all.
Raw generated responses and all failures were retained. Manual inspection
confirmed the factual contents described above and the four successful outcomes;
the scorer was not changed after seeing this batch.

All six interactions left Task rows, Time Blocks and Activity State unchanged.
The rename produced a pending proposal without a receipt; tracking remained an
unconfirmed proposal. The three published regressions are known cases and are
separate from any future unseen evaluation.

## Usage and reproducibility

- **12 actual OpenRouter model calls**, with no automatic retries.
- **104,500 input tokens; 1,389 output tokens**, from provider usage metadata.
- **US$0.0134779689 reported model cost** across all 12 calls; none have unknown cost.
- US$10 pilot ceiling within the accepted US$200 experiment ceiling.
- Production model, 4,096 maximum output tokens, tools, parser and lifecycle were
  preserved. Provider maximum-price bounds were added to enforce accounting.
- Fixture time: 2026-10-08 15:30 Asia/Singapore. Every interaction used a fresh
  synthetic SQLite account. No customer database or confirmation endpoint was used.
- All target-turn answers came from the live model. No controlled scripts were
  used for these six answers. These cases did not require seeded histories.
- The 30 held-out cases were not loaded or executed by the live runner.

Reproduce a separate paid diagnostic batch from `backend/` with:

```powershell
uv run --extra eval python -m scripts.presentation_live NEW-BATCH-NAME
```

The runner refuses to overwrite a batch. It persists a conservative reservation
before every request, keeps unknown costs reserved, and shares a durable US$200
ledger across batches. It records verified endpoint pricing, source-file hashes,
dataset provenance, actual model inputs and tool schemas, streamed outputs,
results, timings, token usage and charges.

Local evidence (ignored artifacts):
`artifacts/presentation-experiment/live-baseline-2026-10-08-a/` contains the source
manifest, pricing snapshot, six JSONL transcripts and `summary.json`.
`artifacts/presentation-experiment/budget.json` is the shared cost ledger.

Issue #315 remains open. Next is a development comparison against a small manually
improved presentation prompt; compilation still requires the agreed baseline
review and correctness/latency/token criteria checkpoint.
