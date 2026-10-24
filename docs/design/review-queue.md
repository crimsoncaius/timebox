# Review queue — progressive prototype

## Fixed decisions

- Android Assistant → Review remains the destination.
- Tracking executes first. No confirmation or Undo in this flow.
- Corrections preserve the original receipt; unsynced tracking stays durable locally.
- Inherit Timebox typography, palette and native Compose controls.

## Round 1: scanning recorded results

Question: can activity name and time lead the queue, with spoken input and original values behind Details?

Prototype: debug route `timebox://prototype/review-queue`, `ReviewQueuePrototype.kt`. Uses local sample data only, with Reset and Today/Empty/Offline controls. No backend mutations. Ordinary Review remains unchanged.

Recommendation: result-first rows. The existing screen gives requests, timestamps, history and controls nearly equal weight. This round promotes activity/time, uses explicit per-item Looks right, and moves evidence into a bottom sheet. The trade-off is an extra tap to compare with speech.

Try correcting Project work from 09:10 to 09:05, then marking it reviewed. Inspect the changed-later entry and unsynced entry. Sample clock is 12:40 today.

## Provisional choices

- Details and correction use a sheet.
- Accepted: saving a correction also marks the item reviewed. The user should not need a second Looks right action after fixing it. A failed save must retain the draft and leave review status unchanged; production integration must persist correction and acknowledgement together.
- Per-item acknowledgement is visible; bulk review is deferred for this round.

## Open decisions and coverage

- Resolved: spoken input stays visible in every row so it can be compared with the recorded result without opening Details. User accepted this on 24 October; the revised prototype places the full request below the activity time, with natural wrapping. Details retains original-record evidence.
- Category and start-time editing are included. End-time editing was removed following the user's clarification below. Day navigation remains outside this experiment.
- Long queues, grouping across days, bulk selection, removed/split records, loading/failure recovery, large font sizes, interruption and correction conflicts need subsequent rounds.
- Offline is a simulated study state, not a proposed change to production caching guarantees.
- User approved integration on 24 October; production integration is described below. Merge remains explicitly deferred until review.

## Round 1 validation

Debug build passed. Installed and opened the deep link on emulator-5590. Inspected rendered queue; verified correction sheet opens/saves and Looks right removes the entry (count 4 to 3). Reset sample state and left the prototype running. Text entry, keyboard layout and broader state coverage remain for subsequent validation.

## Round 2: correcting category and times

**Superseded scope:** the initial interval editor described below was too broad. The accepted correction scope is category and start time; see the clarification after this round's validation.

Accepted: keep speech visible in each row; saving a correction also marks reviewed. User approved the queue direction and requested easier category/time editing.

Experiment: expanded correction sheet with visible speech, searchable sample categories, and separate Start/End buttons opening native Android time pickers. This follows existing `ActivityTimeField` and task-sheet picker usage. Name remains optional and secondary. Category selection returns to the same draft; picker cancellation leaves times unchanged. Close discards local changes. Running activities keep an open end; changing that lifecycle is not part of correction in this round.

Try changing Project work to Learning, moving its start to 09:05 and end to 12:30, then saving. Compare this with the original typed time field. Is selecting times easy enough, or are small minute adjustments still cumbersome?

Validation covers time order and the study's fixed current time of 12:40. All data remains in memory. Real category data, cross-day historical corrections, overlap policy, server failure/conflict recovery, unsaved-dismissal handling and process restoration remain unresolved production coverage. The Today/no-future capture rule must not silently become a new rule forbidding corrections to older records.

Round 2 validation: debug build passed; inspected the correction sheet on emulator-5590. Selected Learning, changed start to 09:05 and end to 12:30 through native pickers, saved, and verified Learning / 09:05–12:30 in Reviewed with To review reduced to 3. Reset and reopened Project work correction for user review.

## Clarification: review the recorded start

The user's speech starts an activity with an open end. This review corrects the interpreted category and start, not the eventual full interval. Queue rows now show Started at, and correction exposes only that time. Existing later end values are preserved, including when a subsequent activity has already closed the record. An original start receipt is shown as category + start, not a completed interval. Save still acknowledges review.

Evidence: `QuickInterpretation` has one `effective_at`; `quickTrackingKind` maps track to Start/Switch or explicit start Edit; backend quick command validation rejects an effective end. `docs/quick-tracking.md` documents start correction and later tracking reconciliation. Explicit Stop is supported by the mechanism, but its review presentation remains a separate unresolved case; this start-entry prototype must not imply all receipts are starts.

Production integration still needs to preserve correct gap/reconciliation semantics when correcting a start. The prototype only changes its local start field, preserves its end, and prevents a start at or after an existing later end. It does not establish the server conflict policy.

## Production integration

The approved hierarchy and speech are now in `AssistantReviewScreen`, using real
categories, journal records and immutable receipts. The debug study route remains
as design evidence; study controls do not appear in the production screen.

- Start events edit category/name/start only; eventual end is preserved.
- Stop events show Stopped tracking and Stopped at; correction edits only that
  time. Unidentifiable, removed, split or no-longer-ended targets link to Day.
- The `correct-event` endpoint applies the canonical range edit and acknowledges
  its resulting revision atomically. It checks exact retry identity and current
  revision. Future times and overlap are rejected; later start moves leave gaps.
- Real `ActivityTimeField` replaces the sample clock: native time/date pickers,
  historical dates, and explicit DST occurrence selection.
- Offline/unsynced items retain their existing journal path. Review edits and
  acknowledgement wait for connectivity/sync. Rejected saves retain editable
  drafts; unknown outcomes retain exact locked Retry requests. Closing an edited
  draft requires a discard choice. No automatic correction replay.
- Earlier activity appends rather than replacing the loaded queue. Empty,
  failure/refresh, reviewed, changed and recovery states are retained.

Validation: 75 focused backend tests passed (2 existing skips), 427 Android unit
tests passed, and native UI tests cover category correction, stop-only fields,
lost replies and editable rejected drafts. Real provider requests created start
and stop receipts in the isolated backend. A native stop-time correction to 15:20
preserved its original start/receipt and appeared in Reviewed. An independent
code/visual review reported no material blockers. Large-font and physical Samsung
hardware behavior were not newly tested in this round; prior hardware limitation
still applies. The full known-failing instrumentation baseline was not rerun.

Visual system remains inherited from Timebox theme and native controls; no
DESIGN.md/token changes. Pending user review on emulator-5590. No merge or PR.

Final checks: all 4 Android Review instrumentation tests passed, including definitive rejection versus unknown outcome; both existing web Review regression tests passed. Local master is an ancestor of this branch. Live backend remains on port 12096 with its isolated review database. Default-assistant role was restored to Timebox after instrumentation reinstalled the app.
