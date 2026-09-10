# Activity Tracking UI prototype

## Progressive review — current round

The user found the combined A/B/C demo too dense to assess. Apply the supplied progressive-prototype workflow: discuss, build one focused experiment, wait for feedback, then capture and advance.

Round 1: does a compact current-activity strip above the Day timeline make tracking discoverable and understandable? One provisional layout, with start, switch, and stop. Uses the real DayTimeline renderer with memory fixtures and a static navigation shell to avoid the real Layout's storage effects. Existing timeline appearance stays stable. Task types and task selection are fixture simplifications, not settled design choices.

Open http://127.0.0.1:12003/day/2026-09-10?variant=A&round=1 (old variant URLs also show round 1). Try starting with the plan, advance the sample clock, switch to Reading, advance again, and stop. Reset restores the initial state. Build and browser start/stop checks passed. No human verdict yet.

Next questions, provisionally ordered: Focus entry/exit; unnamed activity; planned suggestions; inactivity prompts; correction and recovery. Dependencies may change with review. Do not build dependent rounds before feedback. End with a combined experience review. Android remains a separate native prototype ticket.

The earlier combined experiment is preserved at `?variant=A&round=legacy` (also B/C); its layouts remain unaccepted. The rest of this note describes that earlier experiment.

Throwaway, in-memory UI study for issue 136. Three structurally different prompt layouts share the existing `/day/:date?variant=A|B|C` route in development only. The production app is unchanged unless the development variant parameter is present. A fixture shell mirrors Timebox navigation/style; real providers, API reads and writes are bypassed for this interactive study.

## Run

From frontend, install dependencies with npm ci if needed, then:

    npm run prototype:activity

Open http://127.0.0.1:12003/day/2026-09-10?variant=A

A: inline question. B: side companion on wide screens, stacked companion on phones. C: centered activity with bottom prompt dock. Arrow buttons/keys switch variants; form fields retain arrow-key behavior. Phone width and dark toggles support comparison. This is a responsive web study of Android/web interactions, not a native Android build.

Scenarios & state reveals fixture presets, a clock advance, remote change/stop, simulated return, and the current in-memory state. Late switch previews Writing ending at noon and Lunch beginning then. Unnamed Focus supports naming from its original start or starting now. Planning disables Focus entry. Focus omits Stop actions; remote stop exits it. Offline conflict is a scripted illustration of latest-made-change wins, not an offline synchronization implementation.

Browser reload resets fixtures intentionally. No real wake locks, device signals, notifications, persistence, or sync are requested. The return scenario models the expected visible state rather than testing restart durability. Settings and other navigation provide enough context for review, not complete feature implementations.

## Review status

Awaiting live user feedback. Do not close the decision ticket or promote any variant without a human verdict. The Wayfinder map remains planning-only; accepted design decisions feed the later implementation specification.

Validated: TypeScript/Vite build, Focus control restriction, variant navigation, and late-switch preview/result in browser. No automated tests added for this throwaway code.

### Round 1 feedback and revision

Accepted user direction: tracking should be smaller and less obvious; Start tracking must immediately assume the current plan without asking for a name. Revised the large card into a quiet, right-aligned line above the timeline. Starting uses the current Planned Block, or unnamed when none exists. Explicit Switch retains its editor. Placement/visual revision still awaits review. Build passed; browser verified a single Start tracking click enters recording with Writing a proposal and no form.

### Round 1 verdict

User accepted the revised compact tracking line as discreet enough. Settled: quiet controls above the timeline; Start tracking immediately assumes the current Planned Block without a naming step (unnamed fallback when no plan exists). This is the accepted base for later rounds. Next experiment: Focus entry and exit, carrying this base forward. Focus layout and entry placement remain unresolved; no production implementation authorized.

## Round 2 — Focus entry and exit (awaiting review)

Question: does a quiet Focus action beside tracking lead naturally into a dedicated work screen? One provisional centered activity layout replaces the Day navigation/timeline. The accepted compact tracking base remains unchanged except for the Focus action. Entering while off starts tracking immediately; entering while active preserves the interval. Exit Focus returns to Day with tracking still active. No Stop action inside Focus; explicit Switch is available. Planning, unnamed naming prompts, inactivity, wake/lifecycle and remote events are outside this round.

Review: /day/2026-09-10?variant=A&round=2. Enter Focus, advance the sample clock, exit, and observe the same activity interval. Round 1 remains at round=1. Build passed; browser verified start on entry, absence of Stop/navigation, and interval continuity after exit. Human verdict pending; do not advance dependent rounds yet.

### Round 2 verdict and switch correction

User accepted the Focus experience. Requested correction: switching should primarily ask for required Task Type; Activity Name is optional. Both Day and Focus now share a type-first switch form, disabled until a type is chosen. A nameless activity displays its type. Explicit switches store the selected type and do not inherit the current plan name/link. Immediate Start still assumes the plan; no-plan fallback uses unspecified. Fixture task types only. Build passed and browser verified required type, optional name, and disabled submission before selection. Revised form awaits user review.
