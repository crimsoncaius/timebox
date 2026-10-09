# Assistant presentation optimization (#315)

DSPy 3.4.0 is a production dependency. Optuna is installed with the optional
`eval` extra for MIPROv2 optimization. Application and experiments share
`app/services/assistant_policy.py` and `assistant_agent.agent_events`.

The DSPy predictor owns the presentation instructions and demonstrations. Its
custom adapter executes the existing native agent: the same model messages,
tools, streaming parser, card eligibility and proposal lifecycle. It adds no
extra answering-model request. Optimization wraps this execution with isolated
fixtures and grading; those are not part of the application.

## Policies

Policies are standard DSPy JSON state, saved with `save_policy` and loaded with
`load_policy`. Only instructions and context/answer demonstrations are retained;
grades and fixture diagnostics are excluded. Pickle policies are not accepted.
Set `ASSISTANT_POLICY_PATH` to an absolute path to a reviewed policy to activate it.
Without it, production uses the current built-in instructions through the same
DSPy module. No historical policy is activated automatically.

From `backend/`, export the current baseline (this is not optimization):

```powershell
uv run python -c "from app.services.assistant_policy import NativeProgram, save_policy; save_policy(NativeProgram(), 'baseline-policy.json')"
```

Evaluate any saved policy without training (paid model calls):

```powershell
uv run python -m scripts.presentation_evaluate NEW-BATCH --policy baseline-policy.json --model z-ai/glm-5.3-flash
```

For Luna, use `--model openai/gpt-6-luna --reasoning none`. Each evaluation runs
30 validation cases and three repetitions of the three published regressions.
It records the exact policy, prompt hash, model, dataset and source hashes.

Run optimization (also paid):

```powershell
uv run --extra eval python -m scripts.presentation_optimize NEW-BATCH
```

MIPROv2 compares baselines, searches instructions and up to two bootstrapped
demonstrations, saves native DSPy state and evaluates the reloaded policy.
The application does not train on requests. The total spending ceiling is US$200;
evaluation has a US$10 batch cap and optimization a US$50 batch cap. Unknown
request charges retain conservative reservations until reconciled.

## Dataset and metric

`dataset/` is the current revision (formerly v4): 60 training cases, 30 validation
cases, 30 untouched held-out cases and three published regressions. These are
24 scenario families with five variants each, not 120 independent scenarios.
Train/validation/test families do not overlap. Runners do not load the held-out
split. All cases use an isolated synthetic account and frozen clock.

Older revisions are provenance, not additional training data. They are archived
under `docs/experiments/assistant-presentation-315/datasets/`; v2-v4 broadened
validation wording expectations. Do not combine all four revisions.

The metric gives credit only for `pass`. It validates completion and client
acknowledgement, requested facts, grounded/relevant cards, exact proposal
operations, pending confirmation, and unchanged domain data. Unrecognized wording
is `unresolved` and receives zero credit. Text-only answers may omit a selector;
explicit selectors must remain valid and leading. Card answers require a valid
selector referring to eligible snapshots. This is a deterministic task-specific
grader, not a proof of every unsolicited prose claim.

`probe` scripts verify fixtures and the grader; they are not model outputs or
training demonstrations. Model inputs exclude expectations, case IDs and grades.
Evaluation runs serially because database and clock overrides are process-wide.
Keep MIPRO `num_threads=1`.

## Verification and traces

```powershell
uv run --extra dev --extra eval python -m pytest tests/test_presentation_compile.py tests/test_presentation_dataset.py -q
```

Tests cover bootstrap/search wiring, saved-policy model-message parity, runtime
configuration, cancellation, parser failures and native card/proposal lifecycles.
These controlled tests incur no model charges and do not establish model quality.

Add `--trace` to live evaluation or optimization to export to the configured
Phoenix collector in `timebox-presentation-experiments`. For local Phoenix:

```powershell
$env:ASSISTANT_TRACE_ENDPOINT = "http://127.0.0.1:12022/v1/traces"
```

Case roots contain grades and correlate model/tool spans. Grades are trace
attributes, not Phoenix Experiments records. Verify collector delivery separately.
Ignored `artifacts/presentation-experiment/` holds detailed transcripts and budget
records. Historical results and their interpretation are indexed in
`docs/experiments/assistant-presentation-315/README.md` (repository-relative).

## Merge validation — 2026-10-09

After integrating master `0b1577b8`, the full backend suite at `06491d7e` reported
835 passed, 22 skipped and seven failures. All seven failures reproduced on
unchanged master using the same Python environment: four cases in
`test_legacy_cutover_migration.py` and one each in `test_plan_now_migration.py`,
`test_planned_block_name_migration.py`, and
`test_planned_block_type_resolution_migration.py`. Each encounters
`table assistant_queries already exists` during migration 040. They are existing
baseline failures; no migration changes are included here. Changed-file lint
passed. No paid model calls or held-out evaluations were performed for this check.
