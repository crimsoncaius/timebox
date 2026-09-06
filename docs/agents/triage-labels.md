# Tracker Labels

Labels answer separate questions about an issue. Keep those dimensions distinct:

- **Kind** — what sort of report is this?
- **Triage state** — what must happen next?
- **Scope** — is this directly implementable, or does it need decomposition?
- **Work mode** — what kind of decision-making work does a Wayfinder child perform?

Do not use labels as a substitute for acceptance criteria, issue hierarchy, native dependencies, or prioritization fields.

## Kind

Use GitHub's standard `bug` and `enhancement` labels. A redesign, feature, or tweak is normally an `enhancement`; its title and body carry the more precise description.

## Triage state

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

Edit the right-hand column to match whatever vocabulary you actually use.

Use `wontfix` only when the repository has intentionally decided not to action an issue. An implemented issue should instead be closed with GitHub's `completed` state reason.

## Scope

| Label              | Meaning                                                                                          |
| ------------------ | ------------------------------------------------------------------------------------------------ |
| `scope:initiative` | A cross-cutting product outcome that must be decomposed into decision-complete, reviewable work. |

An initiative typically spans multiple product surfaces or technical areas, contains unresolved product or architectural decisions, or requires several dependent implementation issues. The initiative is the parent outcome; its child issues are the units that become `ready-for-agent` or `ready-for-human`.

Do not add `scope:feature` or `scope:tweak`. Those issues are already directly discussable through a bounded goal, preserved behavior, acceptance criteria, and explicit exclusions. Add more scope labels only when a recurring tracker query cannot be answered without them.

Do not encode estimates as `small`, `medium`, or `large` labels. Decompose work that is not reviewable as one outcome; if scheduling estimates become useful, store them in a GitHub Project effort field.

## Wayfinder work mode

The `wayfinder:map`, `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling`, and `wayfinder:task` labels describe the role an issue plays in a Wayfinder decision map. `scope:initiative` may accompany `wayfinder:map`: the former makes the product scope searchable, while the latter identifies the planning workflow.
