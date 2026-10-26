# Android Quick Tracking overlay

Status: design confirmed and implementation authorized, 2026-10-26.

## Agreed behavior

- Replace the system digital-assistant entry's full-screen presentation with a compact overlay, keeping the underlying screen visible. The visual reference has a dark bottom gradient, central orb, and minimal controls.
- Scope is Quick Tracking and later correction in Assistant Review. This is not a voice interface to Assistant Conversation.
- Start listening on arrival. Submit automatically when the user stops speaking, without a confirmation tap.
- Provide no conversational answer or spoken response.
- Briefly show a success indicator after recording, then dismiss the overlay.
- If understanding or recording fails, keep the overlay open with a short error and an explicit retry option.
- Voice only: no typed input or keyboard control.
- Do not display recognized speech or repeat the request back to the user.
- Closing before submission discards the capture. Closing after submission lets the submitted request finish; dismissal is not cancellation.
- The Quick Settings tile opens the same overlay as the system assistant gesture.
- The normal presentation has only a central orb and close button; there is no left-hand control. The orb visually distinguishes listening, processing, and success.
- When microphone access or speech recognition is unavailable, explain the problem and offer the relevant permission/settings action or Retry. Do not introduce a typing fallback.
- Retry is shown for failures rather than as a persistent normal-state control. Request failures also offer Speak again so a misheard command can be replaced; Retry preserves recovery of the existing request.

These choices retain the Quick Tracking execution model in [ADR 0014](../adr/0014-assistant-proposes-tracking-client-applies.md). No new domain term or architectural decision is needed yet.

## Implementation

The existing Quick Tracking activity uses a translucent window with its own task affinity, so system assistant and Quick Settings launches preserve the foreground screen underneath. The capture controller and activity journal remain unchanged. Setup retains its opaque screen and launches capture separately.

The orb uses a monochrome shaded surface with breathing motion for listening, faster motion for processing, and a checkmark for success. Errors provide short recovery text above the controls. Accessibility descriptions expose the state without displaying transcription. Success dismisses after 900 milliseconds.

No new permission to draw over other apps and no additional voice interaction service are required for this presentation.

## Validation and review

- Debug application and instrumentation APK builds passed.
- Both QuickTrackingTest unit tests passed.
- QuickTrackingOverlayTest passed on Android 36, checking hidden transcription, absent text input, recovery actions, close, and success semantics. Two earlier attempts hit process-start ANRs during emulator startup; the settled emulator passed.
- The updated application was launched via ACTION_ASSIST over Android Settings. [Listening](quick-tracking-overlay/review.png) and [retry](quick-tracking-overlay/retry.png) captures verify transparency, minimal controls, and recovery contrast. These PNGs are emulator screenshots of this implementation, not shipped product assets.
- Independent bounded code and screenshot review: ship for user review within the tested scope. The isolated monochrome overlay preserves the global DESIGN.md system.
- Live speech-to-backend recording and physical-device assistant gestures were not exercised. The existing speech submission/controller/journal path remains in place.

User accepted the review and authorized merging into master on 2026-10-26. Review device: emulator-5602, reservation `707ad00cea844728b96830a4859fa3bf`, under `C:/Users/Caius/TimeboxRuntime/emulators`.

