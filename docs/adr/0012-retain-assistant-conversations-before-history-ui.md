# Retain Assistant conversations before exposing history

Capture submitted messages and all Assistant response attempts, including unanswered messages, stopped or partial responses, completion status, and displayed plan cards, so future uses such as revisiting conversations remain possible; completed exchanges alone would irreversibly omit part of that record. Retaining this record does not introduce a conversation-history interface or cross-conversation memory: Android begins with a fresh view after an app process restart, while earlier conversations remain retained, and inactivity while the app remains open must not force a conversation reset. Remove the temporary-conversation heading and limits explanation, retain a quiet New conversation action, and leave the message layout unchanged.

## Retention and response context

Retain captured conversations without automatic expiry. New conversation starts a fresh view and fresh response context without deleting the previous record. Replace the 20-exchange conversation cap with a rolling response context containing the latest 20 acknowledged completed exchanges; retain older exchanges in the stored record and the active session's visible transcript. Older details may therefore fall out of response context without ending the conversation. Stopped and incomplete attempts remain captured but are not eligible response context.

## Capture failures

Save a submitted message before generating its answer; if saving fails, show a retryable error instead of generating an unrecorded exchange. If saving an answer fails after streaming begins, retain its visible text and clearly report that it was not saved. Capture is a product requirement, separate from diagnostic tracing.
