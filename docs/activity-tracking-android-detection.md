# Android screen evidence and check-ins (#155)

The gated Activity Tracking development application observes Android's real UsageStatsManager screen transitions. Android 9/API28 and later expose historical SCREEN_INTERACTIVE and SCREEN_NON_INTERACTIVE events; Android 8/API26–27 is explicitly unsupported. Usage access is special access offered through a deliberate Settings action. Recording, Focus and the shared in-app question do not depend on it. POST_NOTIFICATIONS and the Activity check-ins channel are independent permissions.

This is approximate device sensing: a positive screen-off transition followed by a screen-on transition (or confirmed current noninteractive power state) provides a bounded off interval. It says nothing about whether a person continued working off-device. Screen-on without interaction is not classified as idle. Missing events, absent Timebox interactions and app visibility never establish inactivity. Positive screen-on, keyguard-hidden and user-interaction events can contribute active observations. No package names or usage history are sent to the server.

Permission loss, unavailable history, stopping detection, clock discontinuity and reboot discard the observation boundary. History queries are bounded to nine hours, within Android's short retention window; intervals without an explicit off start are unknown. A generation/rearm change captures a new boundary while retaining the OS transition needed to explain a continuing screen-off state. Candidates count only after that boundary, the shared armed boundary and synchronized active evidence. Saved boot/clock/permission/history bounds permit querying actual OS history after process death. The adapter does not reconstruct observations from app absence. After reboot it waits for fresh coverage, including first device unlock when Android history is unavailable.

WorkManager checks periodically with its minimum 15-minute, inexact schedule; a foreground lifecycle loop checks on return and every 30 seconds. Doze, force-stop and OEM policies can postpone execution. No foreground service, exact alarm or security-lock bypass is requested. Notification timing at the selected threshold is not guaranteed.

Only a locally qualified candidate can enter delivery. Its local interval is marked consumed before transport. The candidate must be acknowledged online, its operation and installation must match the canonical question origin, and a separate server delivery claim must match the operation acknowledged to this installation. A durable attempt is written before the OS call. Offline candidates remain useful in-app but never gain notification eligibility by reconnecting; crash after claim can lose the optional notification. Granting notification permission after a question exists does not escalate it. OS dismissal leaves the question pending. Opening revalidates the current identity, navigates to Day or the current Focus question and reopens a previously dismissed sheet. Sync clearing cancels the OS notification.

No production data was migrated or deployed. Verification uses application com.timebox.android.activitydev and the isolated activity_review server. The opt-in ActivityCheckInPlatformTest probe requires instrumentation argument platformReview=true and the development build; it reads real platform events and can set the documented minimum threshold through ActivityRepository. Ordinary instrumentation runs skip these probes.

## Sources

- [Android UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event): API boundaries and screen/interaction/startup/shutdown events.
- [Android UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager): special usage access, limited event retention and first-unlock restrictions.
- [WorkManager requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work): inexact periodic work.
- [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby): deferred jobs/network, no exact-threshold promise.

## Verification

Focused JVM suites pass: ScreenEvidence 7, CheckInDelivery 6 and ActivityRepository 9 tests. Delivery tests verify acknowledged ownership, offline replay, denial, crash after durable attempt and failed attempt storage. Real platform checks and final review outcomes are recorded below after the full minimum-threshold observation completes.
