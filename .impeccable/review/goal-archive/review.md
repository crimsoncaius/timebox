# Goal archive prototype — 4 October 2026

Scope: debug-only local-state design comparison for issue 306; production archive implementation awaits user choices. No reactivation is accepted. All other choices remain provisional.

Build: debug APK successful with isolated `.goalarchive` package and unreachable loopback API configuration. Device: managed `emulator-5586`, 1080×2424; light/font 1.0 and dark/font 1.3. Current detail sheet is opaque; `period-blocks.png` and `period-dark-large.png` show the corrected version.

Verified on-device: both A/B entry points, all-time archive, past week including archived goals, per-goal period list, week-based history alternative, historical 6h/8h targets, contributing recorded-time sample, archive confirmation and removal from active list, rediscovery in archive, separate delete confirmation and removal. Reset to original sample data and variant A for user review.

Independent finish reviewer: **ship** for the native design comparison. Persistence, fidelity and comparison quality passed with no material fixes. Independent documenter: no durable system changes; existing `DESIGN.md` preserved.

Limits: synthetic data and local mutations, no real backend/persistence validation; no full instrumentation, tablet, TalkBack or physical-device gesture checks. Empty/offline toggles are exploratory simulations. Main-list day selection and full creation/editor interactions reuse production in eventual implementation and are simplified in this round. Missing PRODUCT.md and formal direction/quality cards limit the review to the scoped comparison, not production readiness.

Review ownership and repeatable launch are in `docs/design/time-goal-archive.md`.
