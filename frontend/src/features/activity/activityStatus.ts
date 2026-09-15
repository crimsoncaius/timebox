export type StatusFlags = {
  offline: boolean
  pending: boolean
  busy: boolean
  hasSnapshot: boolean
  error: string | null
}

export function compactStatusLabel(flags: StatusFlags) {
  if (!flags.hasSnapshot) return 'Connection required'
  if (flags.offline && flags.busy) return 'Offline · Saving…'
  if (flags.offline && flags.pending) return 'Offline · Unsynced'
  if (flags.offline) return 'Offline'
  if (flags.busy) return 'Saving…'
  if (flags.pending || flags.error) return 'Unsynced'
  return 'Synced'
}

export function statusAttention(flags: StatusFlags) {
  return flags.offline || flags.pending || flags.busy || !flags.hasSnapshot || flags.error != null
}

export function statusMarkKind(flags: StatusFlags) {
  return statusAttention(flags) ? 'chip' : null
}

export function statusShowsRetry(flags: StatusFlags) {
  return flags.error != null || flags.pending
}
