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

## Round 3 — unspecified activity in Focus

Previous round switching correction accepted by user's request to advance. Round 3 opens at 13:30 with an unspecified interval since 13:20, no current plan, and Focus active. Persistent inline question: What are you doing right now? Required task type and optional name; no dismissal or Stop. Apply from 13:20 describes the full current interval; Start now preserves earlier unspecified time. Choosing a meaningful task type resolves the prompt even without a name, reflecting the user's optional-name clarification. This interpretation and visual treatment await review.

URL: /day/2026-09-10?variant=A&round=3. Reset repeats fixture. Previous rounds remain accessible. In-memory entry names stay optional; timeline display projection falls back to type name. Build and browser checks passed for applying from original start and splitting at now. No dependent round until feedback.

### Round 3 verdict

User accepted the prompt and timing choice. Settled: persistent inline question for an unnamed unspecified activity in Focus; required task type, optional name; applying describes the current interval from its original start, while Start now preserves earlier unspecified time. A meaningful type resolves the prompt without requiring a name. Next unresolved round: quiet planned-block transition suggestions while tracking continues; no automatic activity switch.

## Round 4 — planned activity suggestions (awaiting review)

Question: is a quiet inline suggestion noticeable enough without interrupting Day or Focus? Opens at 11:55 while tracking Writing a proposal since 10:00. Advance +5 min to noon: Lunch is planned now, with Switch to Lunch. Day places it below compact tracking; Focus places it below activity controls. Existing accepted layouts remain otherwise stable. No automatic switch or dismissal requirement. Suggestion ends when its plan ends, tracking stops, or that plan is adopted. Switching assumes plan details and starts at the current time, preserving earlier actual time. Late correction is a later round.

URL: /day/2026-09-10?variant=A&round=4. Reset repeats the scenario. Build passed. Browser verified no suggestion before noon, tracking unchanged at noon, suggestion visible in Day and Focus, and explicit switch at 12:05 starting Lunch then and clearing suggestion. User verdict pending.

Round 4 refinement: user requested very slightly more visibility. Added a faint surface background, modest padding, and medium-weight suggestion text; placement and behavior unchanged. Awaiting review.

### Round 4 verdict

User accepted the refined suggestion with faint background and stronger text.

## Round 5 — inactivity check-in (awaiting review)

Question: does a persistent inline check-in fit the accepted Day and Focus layouts without interrupting work? One layout begins with a simulated one-hour device-inactivity prompt. Tracking continues unanswered. Still doing this clears the prompt without changing recorded time. Switch opens the accepted type-first form; applying a switch clears the pending question. Stop is available in Day only. Focus entry/exit preserves the pending question. Reset repeats the sample.

URL: /day/2026-09-10?variant=A&round=5. Device detection, notifications, re-trigger scheduling, and earlier-time corrections are not implemented in this placement experiment; immediate switch/stop only for now. Later recovery round covers time corrections. Browser verified persistence while advancing time and entering Focus, absence of Stop in Focus, and confirmation preserving the original start. Build passed. Await human feedback before dependent rounds.

Round 5 refinement: separate the muted Still doing this? label from the activity on its own larger, semibold line, as requested. Shared by Day and Focus. Awaiting review.

Round 5 action hierarchy refinement: filled Still doing this primary button; outlined Switch activity secondary button. Stop remains tertiary outside Focus. Requested to make actions clearly recognizable as buttons.

### Round 5 verdict

User accepted the refined inactivity prompt: small muted question label, larger semibold activity name, filled Still doing this primary button, outlined Switch activity secondary button, and quieter Stop outside Focus only. Placement and persistent nonblocking behavior accepted. Next unresolved experiment: correcting an activity switch or stop to an earlier time, with an affected-time preview. Offline/remote recovery and combined review remain later work.

## Round 6 — earlier switch or stop (awaiting review)

Question: is the time boundary and affected recording clear before applying a correction? Sample clock 12:15, Writing since 10:00. Switch adds a time field (defaults now), 15 min ago shortcut, and preview of old/new intervals. Stop in Day opens a matching preview with later time untracked. Focus supports switch corrections without Stop. Time is bounded to current interval, not future; wider historical corrections remain later scope. Cancel leaves recording unchanged. This round provisionally changes Stop to a preview-first flow for evaluation.

URL /day/2026-09-10?variant=A&round=6. Browser verified switch to Break at noon retains Writing 10–12 and records Break 12–12:15; earlier stop leaves Writing 10–12 only. Earlier rounds remain accessible. Await review before recovery/combined rounds.

Round 6 design refinement: user found functionality adequate but design lacking. Revised correction forms into bounded panels with title, grouped timing controls, aligned activity/time rows under After this change, and primary action at the bottom right. Behavior unchanged. Build passed; revised switch panel left open. Visual verdict pending.

### Round 6 verdict

User accepted the revised correction panel design and requested the next round.

## Round 7 — offline recovery feedback (awaiting review)

Question: does a compact offline/synced status plus a short remote-change explanation make reconciliation understandable? Sample starts offline at 12:30, Writing 10–12 and Lunch from noon. Reconnect simulates receiving a newer change: Reading since 12:10. Keeps earlier time, replaces only the later interval, displays nonblocking notice. Same status/notice available in Focus. Correction controls remain available.

URL /day/2026-09-10?variant=A&round=7. This is a scripted visual scenario, not real persistence or conflict resolution. Reconnect always supplies the winning remote change; arbitrary mutation-order conflict cases are outside this experiment. Build passed; browser verified Writing retained, Lunch ends 12:10, Reading begins 12:10, and synced notice. Await review; combined experience and wider history corrections remain unresolved.
