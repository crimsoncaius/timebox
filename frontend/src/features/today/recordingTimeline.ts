import type { PlannedRecordingResult } from '../../lib/api'

export interface RecordingPiece { title: string; start: number; end: number; fresh?: boolean; running?: boolean }

/** Uses the server's frozen interval, including the end of continuing tracking's replaced portion. */
export function recordingTimeline(preview: PlannedRecordingResult) {
  const start = Date.parse(preview.start_at), end = Date.parse(preview.end_at)
  const last = Math.max(end, ...preview.conflicts.flatMap(row => row.end_at ? [Date.parse(row.end_at)] : []))
    + (preview.conflicts.some(row => !row.end_at) ? 15 * 60_000 : 0)
  const before: RecordingPiece[] = preview.conflicts.map(row => ({ title: row.name || row.task_type.name,
    start: Date.parse(row.start_at), end: row.end_at ? Date.parse(row.end_at) : last, running: !row.end_at,
  })).sort((a, b) => a.start - b.start)
  const after: RecordingPiece[] = before.flatMap(row => [
    ...(row.start < start ? [{ ...row, end: start, running: false }] : []),
    ...(row.end > end || row.running ? [{ ...row, start: end }] : []),
  ])
  after.push({ title: preview.replacement.name || 'Unnamed activity', start, end, fresh: true })
  after.sort((a, b) => a.start - b.start)
  return { start, end, first: Math.min(start, ...before.map(row => row.start)), last, before, after }
}
