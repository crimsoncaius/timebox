# Optional selector for text-only Assistant answers

The user approved changing the output contract: ordinary text without a selector
means no cards. A leading none-selector remains accepted. Explicit selectors still
require valid schema and eligible snapshots; card count and uniqueness checks are
unchanged. An initial JSON object remains reserved for presentation selection.

The parser streams plain text, including long answers and leading whitespace.
It buffers possible control-object prefixes within answer text across arbitrary
chunk boundaries and rejects misplaced selectors before emitting their JSON.
Ordinary braces in prose remain text. A trailing selector is not relocated or
silently stripped. During forced-final streaming, preceding prose may already be
visible when a later selector causes interruption; that attempt cannot complete.

The production presentation prompt now describes the optional none-selector.
No historical compiled policy or experimental transcript was overwritten.

## Validation

- Broad Assistant and presentation suites: 325 passed, 14 skipped. The final
  parser-only rerun after adding malformed trailing-selector controls: 35 passed.
- Every chunk boundary is covered for plain text, Unicode, long answers, explicit
  card headers, and late/malformed selectors. Existing unknown-ID, duplicate-ID,
  card-only terminal, cancellation and truncation checks remain.
- Native API tests confirm that plain-text proposal responses complete and remain
  pending confirmation without applying mutations. Malformed selector responses
  still cannot activate proposals.
- A plain-text answer to a card-required request is delivered, but correctly fails
  the grader's required-card check. Reading alone does not create a displayed card.
- The two actual failed responses from `glm-compiled-2026-10-09-a` (free-gap question
  and third subtask-state repetition) were replayed unchanged, with controlled
  reproduction of their original read calls. Both complete, acknowledge and pass
  the same factual expectations with unchanged domain state. These are parser
  regression replays, not a new live-model score or retraining run.
- Replay evidence: `artifacts/presentation-experiment/optional-selector-replay-2026-10-09.json`.
  No API spending. Phoenix export was attempted but reported an export failure;
  local replay evidence is the verification record.
- Ruff and `git diff --check` pass. The final backend was restarted from this tree
  on port 8001 using the isolated review SQLite account.

Changes remain on the working branch for review. No PR or merge was made.
