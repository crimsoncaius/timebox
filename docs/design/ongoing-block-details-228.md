# Ongoing Actual Block details

The user selected variant A on 19 September 2026: use the saved Actual and
Planned Block details hierarchy, preserving the compact summary and explicit
expansion to edit. The elapsed duration must update while recording continues.

The [three-option prototype](https://github.com/crimsoncaius/timebox/tree/codex/228-ongoing-block-prototype)
is preserved separately as the design source for issue #228.

The Android running sheet uses the shared Actual badge, large time range,
elapsed duration, field typography, spacing, and linked Battle Plan Task group.
Its summary shows identity, Task Type, and Note. Editing exposes Start, a
read-only ongoing End, Block Name, Task Type, and Note. Switch and Stop remain
the existing tracking flows, with the selected outlined/filled action hierarchy.

Elapsed duration derives from the recording repository's calibrated clock. The
lifecycle-aware one-second refresh remains active in both summary and editing
states and resumes when the app returns to the foreground. It displays whole
elapsed minutes using the app's duration format; the Actual Block's end stays
null until tracking is switched or stopped.
