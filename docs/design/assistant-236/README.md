# Assistant conversation and plan-card review — throwaway

Question: does the selected Conversation layout with an embedded plan card work across the presentation, memory, and interruption decisions?

Run `python docs/design/assistant-236/serve.py`, then open http://127.0.0.1:12044/?variant=A.

Adapted from the Conversation design on `codex/prototype-assistant-227`. Compare three arrangements within that chosen direction using the floating arrows or the variant URL parameter:

- A: inline schedule before interpretation (recommended starting point).
- B: compact schedule disclosure.
- C: interpretation before supporting schedule.

Use the scenario selector for formatted answers, card-only completion, empty plan, read failure, interrupted/unconfirmed responses, a long plan, long conversation, midnight, expiry and limits. Starter prompts populate the composer for editing. Send streams a deterministic sample response; Stop retains partial output; Retry appends a fresh attempt. The Day navigation preview lets a stream continue off-tab. New conversation invalidates pending output. Display controls simulate a 320px phone, larger text, dark theme, and keyboard inset. Prototype state exposes which completed attempts would enter memory.

Browser checks: syntax, appended retry preserving the interrupted attempt, stable output after Stop, preserving scroll position during streaming, reset isolation, expiry without Retry/composer, URL variant switching, and disclosure opening. Visually inspected the default layout and small/dark/large-text/keyboard combination. Browser console had no errors.

Limitations: fixture data and scripted timing, no real AI, no actual contextual reasoning or backend acknowledgement, no persistence, and only simulated navigation/keyboard/expiry. The schedule expansion and disclosure are presentation controls, not navigation to Planned Blocks. Native Android IME, TalkBack, text scaling, and real transport behavior still require implementation validation. All dates are fixture dates. The switcher lives only in this standalone prototype, outside production routes/builds.

User verdict: "A is perfect." Variant A (inline schedule) is the approved visual direction. The resolution and its scope are recorded in [Review the combined conversation and plan-card interaction](https://github.com/crimsoncaius/timebox/issues/236). Native Android validation and the final implementation acceptance criteria remain outstanding. Implementation remains out of scope for the Wayfinder map.
