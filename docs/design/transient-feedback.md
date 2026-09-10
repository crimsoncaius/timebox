# Transient Feedback

Issue #129 selected prototype A, Floating paper: a raised neutral surface, 24px corners, a fine tonal border, quiet elevation, compact body text, a tonal pill action, and a named close icon where dismissal is supported. Both themes use the application's surface and content palette. Errors use the error text color; timeline colors retain their existing meaning.

The family covers general feedback, Trash undo, Task Completion undo, recorded-Actual undo, and web in-app task reminder cards. Inline validation, persistent page errors, loading indicators, and browser/Android operating-system notifications remain outside this component contract. In-app task reminders are distinct from device-local Daily Reminders.

The shared components own presentation and accessible controls. Callers own message identity, mutations, timers, busy state, dismissal, and recovery. Action labels retain their actual meaning: Undo, Retry, or Open. Long names wrap, action targets remain reachable, and keyboard focus is not moved into a new message.

Action feedback appears at bottom center, with Trash recovery taking priority over a pending completion notice on Battle Plan. On Day, recorded-Actual undo takes priority over completion undo, then general feedback. Suppressed notices retain their state. Android's existing snackbar queue waits while Trash recovery owns the slot. Web reminders remain a separate bottom-right stack and move above the feedback slot on narrower screens. Device insets and navigation clearance are retained.

Only the appearance and presentation priority were approved. The proposed 4/10/12-second uniform lifetime policy was not adopted. Existing behavior remains: web Trash uses ten seconds of eligible exposure, pauses when hidden/hovered/focused, disables actions during restore/expiry, and retains Retry after failure; reminders expire after twelve seconds. Existing web completion/recorded-Actual notices have no new expiry. Android retains its accessibility-adjusted Trash timeout and snackbar durations. Trash's decorative fade continues to honor reduced motion.

The complete A/B/C experiment and selection verdict are preserved in local branch `codex/prototype-feedback-129`, commit `9ffec6b`. Its files and switcher are intentionally absent from production. A was selected by the user for implementation; no alternate variant was promoted.
