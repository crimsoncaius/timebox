# Issue #88: Remove the Plan saved success message

Agreed during the issue #88 grilling session.

Successful Android Plan mode saves return to the timeline with the saved
Planned Blocks visible. This provides sufficient confirmation; remove the
additional "Plan saved" snackbar without replacing it with a flash or overlay.

Keep existing pending-save feedback. A failed save must still show its error
and preserve the active planning session and drafts for retry. Empty exits and
cancellation retain their existing behavior. Other snackbar messages are outside
this change's scope.

This supersedes the issue's original proposal to shorten the success effect's
duration. No success-message timing or animation is needed, including when
reduced motion is enabled. Repeated successful saves must not enqueue success
messages.
