# Activity tracking: platform capabilities

Research for [#132](https://github.com/crimsoncaius/timebox/issues/132), under [#128](https://github.com/crimsoncaius/timebox/issues/128). Checked 2026-09-10 against repository baseline `a999eae`. This is research, not a product decision or implementation specification. No existing research directory was present, so this note establishes `docs/research/`.

## Established product constraints

The map records an explicitly started activity continuing until switch/stop, even during backgrounding or locking. Device inactivity may prompt after a Settings-configurable threshold, default one hour. There are no routine check-ins; silence keeps tracking running with one pending prompt and no escalation. Focus is optional; its entry/exit preserves recording. These are user decisions from [#128](https://github.com/crimsoncaius/timebox/issues/128), not consequences of OS APIs.

## Repository baseline

Android is native Kotlin/Compose, minimum API 26, compile/target API 34, with WorkManager and DataStore ([build](../../android/app/build.gradle.kts)). The [manifest](../../android/app/src/main/AndroidManifest.xml) declares notification and boot permissions but no usage-access permission. The [reminder infrastructure](../../android/app/src/main/java/com/timebox/android/reminders/ReminderInfrastructure.kt) uses network-constrained 15-minute periodic WorkManager sync and notification channels; [daily reminders](../../android/app/src/main/java/com/timebox/android/reminders/DailyReminderInfrastructure.kt) use delayed one-time work. Neither establishes device inactivity detection.

Web is a React 19/Vite browser application ([package](../../frontend/package.json)). A source search found no IdleDetector, screen wake-lock, or service-worker integration in `frontend/src`. Existing visibility handlers establish application visibility only.

## Android: what can be observed

- `PowerManager.isInteractive()` reports readiness for interaction, explicitly not whether a user is interacting. A screen can temporarily be off while the device remains interactive. Doze/asleep and interactive are separate states. It supplies no last-input timestamp. [Android PowerManager](https://developer.android.com/reference/android/os/PowerManager#isInteractive())
- `UsageStatsManager.queryEvents()` offers historical usage events across packages with `PACKAGE_USAGE_STATS`; the user must grant special usage access in Settings, beyond declaring permission. Event history is retained only for a few days. On Android R and later, queries can return null before the user is unlocked after boot; that condition must not be confused with every ordinary screen lock. [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- Public events include `USER_INTERACTION` (API 23: a package was interacted with), and screen-interactive/non-interactive and keyguard-shown/hidden events (API 28). API 26/27 therefore lack those latter historical events. Shutdown/startup events (API 29) also have caveats: shutdown timestamp is last database persistence, not necessarily actual power-off. [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)

**Inference:** these are useful coarse device-use signals, not a documented complete stream of every touch/key or a guaranteed precise last-input clock. Absence of usage events cannot safely be advertised as proof that no interaction occurred. Screen-off duration is a different definition of inactivity from lack of input while reading with the display on. Research does not select that substitute.

Android may kill cached processes, and `onDestroy()` is not guaranteed. Activity-local timers/listeners cannot provide continuous monitoring through process death. Durable timestamps/state and re-evaluation would be necessary for recovery; that is a design implication, not a promise of complete historical observations. [Processes and lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)

## Web: conditional device-wide detection

Chrome documents `IdleDetector` for system activity beyond the page. It requires a secure top-level context, a separate idle-detection permission requested through user interaction, and a minimum threshold of 60 seconds; one hour is valid. It exposes separate active/idle and locked/unlocked states. Content-area input listeners or visibility-based polyfills cannot detect inactivity elsewhere on the device. This is not an interoperable all-browser baseline; feature-detect and test the actual browser/version. [Chrome Idle Detection guide](https://developer.chrome.com/docs/capabilities/web-apis/idle-detection?hl=en)

The API specification exposes it to Window and DedicatedWorker, not ServiceWorker. Its signals describe system inactivity, not whether a person is still doing an offline task. No persistent historical inactivity log is defined. [Idle Detection specification](https://wicg.github.io/idle-detection/)

Frozen pages suspend freezable tasks; discarded pages execute no JavaScript and may receive no final event. A browser tab left open is not a reliable always-running monitor. **Inference:** a dedicated worker does not establish a browser-closed inactivity service; recovery cannot reconstruct an unobserved hour from app absence alone. [Chrome page lifecycle](https://developer.chrome.com/docs/web-platform/page-lifecycle-api)

## Prompt delivery, battery and offline limits

Android WorkManager periodic work has a 15-minute minimum and is inexact; constraints and system optimization affect execution. A one-hour threshold is not a guarantee of delivery at exactly one hour. [Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

Doze defers network, jobs and ordinary alarms and ignores wake locks; maintenance windows are limited. App Standby also limits background work. Keeping a screen awake is not a substitute for background scheduling. [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)

Android 13+ requires notification permission for ordinary notifications; users may deny it. A foreground in-app pending prompt and an OS notification are distinct delivery surfaces. [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)

Web notifications also require permission. Persistent notifications associate with a service-worker registration; a matching tag can replace an existing notification. This supplies presentation/deduplication, not an inactivity detector or reliable scheduler. [Notifications standard](https://notifications.spec.whatwg.org/)

**Design implications:** pending-prompt identity must outlive a rendered dialog and avoid recreation on every poll/restart. A notification being dismissed is not automatically a user correction. Platform-local sensing does not require fetching the server, but existing Android reminder sync requires connectivity. Offline shared-timeline consistency, cross-device deduplication, and prompt resolution need an explicit synchronization contract; neither platform API resolves them. Browser lifecycle limits still apply offline.

## Focus display wake behavior

Android `FLAG_KEEP_SCREEN_ON`/`keepScreenOn` applies to an activity; when backgrounded the screen may turn off normally. Clear it when Focus no longer requests wake behavior. It needs no wake-lock permission. This prevents normal display timeout while visible, rather than supplying a background CPU service or a right to bypass security locking. [Android keep-screen-on guide](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on)

Web `navigator.wakeLock.request('screen')` requires an active visible document and secure context; hidden/inactive documents release locks. The browser may reject/release for low battery or power saving. Manual screen-off makes the lock inapplicable. Returning to visible Focus requires re-evaluating and requesting again; successful prior acquisition is not durable. The API can affect normal automatic locking, but does not promise overriding OS/manual lock policy. Focus expiry and application-level state must remain separate from lock acquisition/release. [Screen Wake Lock specification](https://www.w3.org/TR/screen-wake-lock/)

## Existing decisions requiring explicit reconciliation

[#39](https://github.com/crimsoncaius/timebox/issues/39) and the [product spec](../specs/task-timeboxing-product-spec.md) derive Actual Blocks from current Planned Blocks after one minute, stop on plan end/Work Mode exit, and ask for backfill confirmation after more than ten minutes away. These rules conflict with accepted #128 direction: explicit activity controls recording, app absence alone is not uncertainty, and leaving Focus preserves it. The new inactivity prompt must not quietly retain the old ten-minute absence gate.

[#87](https://github.com/crimsoncaius/timebox/issues/87) and its [spec](../specs/issue-87-plan-work-mode-separation.md) prevent concurrent planning and Work Mode and preserve drafts/recording on rejected transitions. Ordinary all-day tracking now permits full-app access; the map must explicitly assign any remaining planning restriction to Focus or retire it. Research does not choose that behavior.

## Remaining decisions and validation implications

Decide what detectable evidence qualifies on Android; permission-denied/unsupported/unobserved behavior; whether delivery on return is acceptable when background execution is unavailable; one pending prompt per device versus shared activity; Settings threshold bounds; and Focus wake request/restoration/expiry policy. None is decided here.

Implementation validation should cover Android API 26/27 versus 28+, denied/revoked usage and notification access, restart/reboot, Doze and offline return; supported/unsupported web browsers, permission denial, hidden/frozen/discarded/closed tabs; and manual lock, low battery, Focus exit/expiry and wake-lock reacquisition. A platform test must not infer person activity from device inactivity.
