# Tracker Labels

Labels answer separate questions about an issue. Keep those dimensions distinct:

- **Kind** — what sort of report is this?
- **Triage state** — what must happen next?
- **Scope** — is this directly implementable, or does it need decomposition?
- **Work type** — research, prototype, or (by default) implementation?

Do not use labels as a substitute for acceptance criteria, issue hierarchy, native dependencies, or prioritization fields.

## Kind

Use GitHub's standard `bug` and `enhancement` labels. A redesign, feature, or tweak is normally an `enhancement`; its title and body carry the more precise description.

## Triage state

Every open issue carries exactly one state label. The one exception: a Wayfinder
map child waiting on a blocker carries none until the runner marks it ready; its
native dependency already shows why.

| Label             | Meaning                                                        | Set by                                    |
| ----------------- | -------------------------------------------------------------- | ----------------------------------------- |
| `needs-triage`    | Needs classification with codebase access                      | Timebox Intake bot, or anyone filing an issue |
| `ready-for-agent` | Decision-complete; the runner starts an autonomous agent       | Timebox Intake bot, a triage agent, or Timebox Review bot recording an answer or feedback |
| `in-progress`     | An autonomous agent is working on it                           | The runner                                |
| `needs-info`      | Waiting on the owner's answer, choice, or decision             | An agent                                  |
| `in-review`       | Report posted; waiting for the owner's approval                | An agent                                  |
| `approved`        | Owner approved; the runner merges it, or starts a map's children | Timebox Review bot                      |

When a mattpocock skill names one of its five canonical triage roles, use
`needs-triage`, `needs-info`, or `ready-for-agent` (also the AFK-ready role) by
name. This repo has no human-implementation role: work that needs the owner uses
`needs-info`. `wontfix` is a closing reason, below.

An owner working an issue interactively claims it by assigning themselves. The
runner skips assigned issues and issues labelled `in-progress`.

Use `wontfix` only when the repository has intentionally decided not to action an
issue, and close it. An implemented issue should instead be closed with GitHub's
`completed` state reason.

## Work type

| Label            | Meaning                                                              |
| ---------------- | -------------------------------------------------------------------- |
| `type:research`  | Answer a question; delivers findings in `docs/research/`             |
| `type:prototype` | Build throwaway options for the owner to choose between; never merged |

An issue with no type label is implementation.

## Scope

| Label              | Meaning                                                                                          |
| ------------------ | ------------------------------------------------------------------------------------------------ |
| `scope:initiative` | A cross-cutting product outcome that must be decomposed into decision-complete, reviewable work. |

An initiative typically spans multiple product surfaces or technical areas, contains unresolved product or architectural decisions, or requires several dependent implementation issues. The initiative is the parent outcome; its child issues are the units that become `ready-for-agent`.

Do not add `scope:feature` or `scope:tweak`. Those issues are already directly discussable through a bounded goal, preserved behavior, acceptance criteria, and explicit exclusions. Add more scope labels only when a recurring tracker query cannot be answered without them.

Do not encode estimates as `small`, `medium`, or `large` labels. Decompose work that is not reviewable as one outcome; if scheduling estimates become useful, store them in a GitHub Project effort field.

## Wayfinder

`wayfinder:map` marks a Wayfinder decision map; `scope:initiative` may accompany it,
making the product scope searchable while `wayfinder:map` identifies the planning
workflow. Map children use the work-type labels above, and an implementation child
carries none. `wayfinder:grilling` marks a child that needs the owner present to
decide; autonomous agents never pick it up.
