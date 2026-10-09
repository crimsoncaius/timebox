# Grader corrections and retained-response regrade

The user requested fixes to the grader after the GLM and GPT-6 Luna comparisons.
No model calls, retraining or prompt edits were made. Both runs were regraded from
their saved native interaction evidence; original transcripts and grades remain
unchanged.

## Corrected scores

| Policy/model | Validation before → after | Regressions before → after |
| --- | ---: | ---: |
| Current GLM baseline | 23/30 → 23/30 | 4/9 → 5/9 |
| Manual GLM baseline | 26/30 → 26/30 | 5/9 → 6/9 |
| Compiled GLM | 29/30 → 30/30 | 8/9 → 8/9 |
| Compiled GPT-6 Luna | 17/30 → 23/30 | 1/9 → 3/9 |

The compiled GLM regression still fails once because its answer omits the header.
Luna still has seven validation failures (five task-change lookups and two card
selections). In its regression set, three rename attempts fail and three subtask
lookups remain unresolved because they never establish the requested child states.
Those are genuine unsuccessful requests, not wording misses. All three Luna Work
breakdowns now pass. The binary metric still gives zero to fail and unresolved.

## Changes

- Duration checks associate each value with its label in inline prose, including
  “9 hours on Work: 6 hours on Coding and 3 hours on Admin” and “6h was Coding”.
  They preserve sentence-level planned/actual context and avoid assigning an
  Admin duration to a later incidental mention of Coding.
- Development expectations recognize retained free-gap paraphrases and evening
  time ranges such as “6:00 to 7:00pm” and “six to seven this evening”.
- Leading/trailing whitespace no longer invalidates an otherwise number-only
  answer. Parsing and acknowledgement still must succeed; a missing selector
  cannot be rescued by stripping the raw model response.
- Negative controls cover swapped totals, wrong values even with correct cards,
  uncertain/negated statements, ambiguous label lists, incorrect gap boundaries,
  morning versus evening, wrong start hours, and additional arithmetic content.

The grader is deterministic and conservative, not a general natural-language
entailment judge. Unsupported expressions can still remain unresolved; this change
addresses the observed development misses with adjacent negative controls.

Validation: 136 focused tests passed, including 33 wording/negative-control cases
and 103 dataset/native-boundary checks. Ruff and `git diff --check` pass. The local
review API remains running on port 8001; the corrected grader was exercised from
this working tree by the retained-response regrade command.

Dataset-v4 records the changed development expectations. Dataset-v1 through v3
are preserved. Held-out and published-regression JSON bytes are identical to v3;
no held-out candidate answers were generated or graded. Shared scorer changes
will apply when a future held-out evaluation is explicitly authorized. Future
live/optimization runners now select dataset-v4.

`scripts/presentation_regrade.py` imports no model runner invocation and makes no
network requests. It records old/new grades and scorer source hashes in a separate
`regrade-v4.json` inside each original batch directory. The full regrade covers
242 GLM-run interactions and 39 Luna interactions. No previously passing response
was downgraded and no previously failed interaction was promoted to pass. The
compiled GLM 30/30 is still a post-search development score, not independent proof
of reliability.

Reproduce for another retained batch from `backend/`:

```powershell
uv run python -m scripts.presentation_regrade BATCH-NAME
```

The command refuses to overwrite an existing regrade. Original run reports retain
their historical frozen-metric scores; this report supplies the corrected results.
Issue #315 remains open. Production prompts, models and tool contracts are unchanged.
