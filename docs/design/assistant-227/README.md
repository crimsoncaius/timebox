# Assistant design prototype — issue 227

Throwaway browser approximation of the Android Assistant, with fixture data only.
Question: should Assistant read as a conversation (A), daily briefing (B), or notebook (C)?

Run from the repository root: `python docs/design/assistant-227/serve.py`.
Open http://127.0.0.1:12033/?variant=A (also B and C).

Use the bottom arrows to compare structures. Review controls expose empty, completed,
streaming, interrupted, expired, limit, and long conversation states, dark theme,
larger text, and a simulated keyboard. Starter prompts fill the composer; Send
streams a fixed sample response. Navigation is visual context only.

Recommendation: A for the conversational scope. No user verdict yet; acceptance
criteria and native implementation remain pending review. B's structured cards
would require an explicit rendering contract decision. This mockup does not
validate Android IME, accessibility services, or backend behavior.

Manually checked in the browser: A layout, B/C switching and URL updates,
streaming-to-interrupted action, and dark empty state with simulated keyboard.

Primary-source branch: `codex/prototype-assistant-227`. Keep prototypes out of master.
