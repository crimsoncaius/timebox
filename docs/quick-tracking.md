# Android quick tracking

The Quick Settings tile and default digital assistant open the same voice capture.
The microphone starts automatically; final speech recognition submits immediately.
Text is secondary. Permission denial, unavailable recognition, and model failures
keep an explicit retry/text path. Leaving capture cancels listening, while an
already-submitted command continues in the application. A retained draft never
executes automatically after restart or reconnect.

Results live in Assistant → Review alongside conversation results. Quick tracking
has corrections but no Undo. Unsynced local actions appear separately until the
canonical journal transaction creates their account-level receipt. Rejected and
superseded operations must be inspected rather than reported as successful sync.

## Setup

Open Settings → Quick tracking → Set up. Add the Quick Settings tile and select
Timebox as the default digital assistant. Setup opens Android's digital assistant
settings and verifies the assigned role on return. Android 36 exposes the role
but rejects `createRequestRoleIntent(ROLE_ASSISTANT)` as not requestable.
The tile works independently
of the assistant role. Locked-device tile invocation requests unlock first.

On supported Samsung devices, also select Settings → Advanced features → Side
button → Long press → Digital assistant. Selecting the default assistant alone
does not replace a side-button assignment to Bixby or the power menu. Check gesture
navigation's assistant gesture setting as well; menus and support vary by model
and One UI release. A stock Android emulator cannot validate Samsung hardware
button dispatch. Speech recognition availability and network requirements depend
on the installed recognition service; the assistant role does not provide speech
transcription by itself.

Platform references:

- [Android assistant role](https://developer.android.com/reference/android/app/role/RoleManager)
- [Assistant eligibility](https://developer.android.com/reference/androidx/core/role/RoleManagerCompat)
- [Quick Settings tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles)
- [Speech recognition](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Samsung digital assistant](https://www.samsung.com/us/support/answer/ANS10001575/)
- [Samsung side button](https://www.samsung.com/uk/support/mobile-devices/how-to-customise-the-side-button-with-new-features-on-your-galaxy-phone-and-tablet/)

## Implementation sources

The current uncommitted `codex/tracking-review-lab` implementation supplied the
concurrent d1-ultra activity/timing prompts, conditional GLM extraction, and strict
Today/no-future rules. Only its interpretation code was extracted; its isolated
SQLite timeline, fixture UI, comparison tools, and Undo stack are not shipped.
`codex/prototype-review-location` selected Assistant Review (commit `361d1f56`).
Production review components were reused from `709b0064`, with quick tracking
integrated through Android's existing offline activity journal.

Request arrival fixes effective “now” and relative arithmetic. Start-time
correction to a later time preserves the gap; earlier tracking replaces recording
onward, and an earlier Stop removes recording onward. Ordinary continuation
preserves its original start. A local activity change during interpretation
requires Retry. New application of an interpretation is limited to fifteen
minutes and the same reporting date; already-journaled actions can sync later.

## Validation

Backend tests cover interpretation routing, time zone/DST boundaries, failed
extraction, stable operation identities, corrections, and receipt atomicity.
Android unit tests cover continuation versus start correction and durable
offline execution/restart. The opt-in `QuickTrackingEntryTest` uses a final speech
recognition callback against an isolated live backend; it verifies automatic
submission and persistence, not acoustic recognition quality. Run it only on an
owned review emulator with microphone permission and the isolated backend set:

```powershell
python scripts/android-emulator.py gradle TOKEN connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.timebox.android.ui.assistant.QuickTrackingEntryTest" `
  "-Pandroid.testInstrumentationRunnerArguments.liveQuickTracking=true" `
  "-PreviewApiBaseUrl=http://10.0.2.2:12096/"
```

Validated on 2026-10-24: 427 Android unit tests; 64 focused backend tests with two
existing skips; the live recognition-callback instrumentation test; live d1-ultra
now/extract/future requests; system tile add/launch; assistant selection in Android
Settings; assistant key and bottom-corner gesture dispatch; and native Review
correction with the original receipt preserved. Android 36 Google Play emulator,
not Samsung hardware. Acoustic transcription remains dependent on the installed
speech service and was not covered by the injected final-recognition test.
