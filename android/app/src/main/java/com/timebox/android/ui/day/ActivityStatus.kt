package com.timebox.android.ui.day

data class StatusFlags(
    val offline: Boolean,
    val pending: Boolean,
    val busy: Boolean,
    val hasSnapshot: Boolean,
    val error: String?,
)

fun compactStatusLabel(flags: StatusFlags) = when {
    !flags.hasSnapshot -> "Connection required"
    flags.offline && flags.busy -> "Offline · Saving…"
    flags.offline && flags.pending -> "Offline · Unsynced"
    flags.offline -> "Offline"
    flags.busy -> "Saving…"
    flags.pending || flags.error != null -> "Unsynced"
    else -> "Synced"
}

fun statusAttention(flags: StatusFlags) =
    flags.offline || flags.pending || flags.busy || !flags.hasSnapshot || flags.error != null

fun statusMarkKind(flags: StatusFlags) = if (statusAttention(flags)) "chip" else null

fun statusShowsRetry(flags: StatusFlags) = flags.error != null || flags.pending
