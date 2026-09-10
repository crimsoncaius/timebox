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
