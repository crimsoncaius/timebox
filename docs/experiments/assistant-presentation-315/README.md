# Assistant presentation experiment history

These reports preserve development evidence for issue #315. They are historical,
not new measurements of the current runtime. Raw transcripts and accounting remain
in ignored local artifacts; reports identify the corresponding batches.

- [Initial live pilot](assistant-presentation-live-2026-10-08.md)
- [MIPROv2 optimization](assistant-presentation-optimization-2026-10-08.md)
- [Luna comparison](assistant-presentation-luna-2026-10-08.md)
- [Grader corrections and regrade](assistant-presentation-grader-2026-10-08.md)
- [Fresh GLM evaluation with tracing](assistant-presentation-glm-traced-2026-10-09.md)
- [Optional text selector and retained-output replay](assistant-optional-selector-2026-10-09.md)

The old `backend/studies/presentation/dataset-v1`, `dataset-v2`, and `dataset-v3`
are preserved byte-for-byte in `datasets/v1`, `datasets/v2`, and `datasets/v3`.
Their historical manifests retain original provenance. Current v4 lives at
`backend/studies/presentation/dataset`. They are revisions of the same split,
not four datasets to concatenate. The held-out split has not been evaluated.

`compiled-exploratory-2026-10-08.json` is the original custom-format export,
preserved unchanged. It predates the optional text selector and native DSPy state
format. Do not pass it to the current evaluator or configure it as the runtime
policy. Future optimization produces standard DSPy JSON state.

Report references to old filenames, runner names and paths describe what was used
at the time. Current commands and architecture are documented in
[the study guide](../../../backend/studies/presentation/README.md).
