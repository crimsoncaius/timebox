import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { BlockDraftPlacement, DayRead, TaskType, TimeBlock } from '../lib/api'
import { formatMinuteLabel24 } from '../lib/time'
import { TaskTypePathCombobox } from './TaskTypePathCombobox'
import { Link } from 'react-router-dom'

export type TimeBlockInspectorVariant = 'rail' | 'sheet'

const NOTE_DEBOUNCE_MS = 450

/**
 * Shared form for editing a time block in the desktop inspector rail or mobile sheet.
 * Start/end times are display-only; adjust duration on the timeline.
 * Task type and note persist automatically (debounced note, immediate task type).
 */
export function TimeBlockInspectorContent({
  block,
  draft,
  day,
  taskTypes,
  variant,
  onClose,
  onSave,
  onCreateFromDraft,
  onDelete,
  onCompleteAsPlanned,
  onCreateTaskTypePath,
  onDirtyChange,
}: {
  block: TimeBlock | null
  draft: BlockDraftPlacement | null
  day: DayRead
  taskTypes: TaskType[]
  variant: TimeBlockInspectorVariant
  onClose: () => void
  onSave: (patch: { task_type_id?: number; note?: string | null }) => Promise<void>
  onCreateFromDraft?: (payload: { task_type_id: number; note: string | null }) => Promise<void>
  onDelete: () => Promise<void>
  onCompleteAsPlanned?: () => Promise<void>
  onCreateTaskTypePath: (path: string) => Promise<TaskType>
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [taskTypeId, setTaskTypeId] = useState(() => block?.task_type_id ?? draft?.task_type_id ?? 0)
  const [note, setNote] = useState(() => block?.note ?? '')
  const [saving, setSaving] = useState(false)

  const isCreateMode = draft != null && block == null
  const noteDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const draftCreateAttemptedRef = useRef(false)

  const clearNoteDebounce = useCallback(() => {
    if (noteDebounceRef.current) {
      clearTimeout(noteDebounceRef.current)
      noteDebounceRef.current = null
    }
  }, [])

  /** Reset local fields when selection or draft changes. Layout effect so task-type auto-save useEffects see a consistent taskTypeId on first paint after open. */
  useLayoutEffect(() => {
    if (block) {
      setTaskTypeId(block.task_type_id)
      setNote(block.note ?? '')
      return
    }
    if (draft) {
      setTaskTypeId(draft.task_type_id ?? 0)
      setNote('')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only reset when selection `block.id` or `draft` changes; omitting `block` avoids wiping local state on every parent day refresh for the same block id.
  }, [block?.id, draft])

  const dirty = useMemo(() => {
    if (isCreateMode) {
      return taskTypeId !== 0 || note.trim() !== ''
    }
    if (block) {
      const newNote = note.trim() || null
      const oldNote = (block.note ?? '').trim() || null
      return taskTypeId !== block.task_type_id || newNote !== oldNote
    }
    return false
  }, [block, isCreateMode, note, taskTypeId])

  useEffect(() => {
    onDirtyChange?.(dirty)
  }, [dirty, onDirtyChange])

  const hasLinkedActual =
    block &&
    day.time_blocks.some((b) => b.lane === 'actual' && b.planned_block_id === block.id)

  const canSaveCreate =
    isCreateMode &&
    taskTypeId > 0 &&
    taskTypes.some((t) => t.id === taskTypeId) &&
    !!onCreateFromDraft

  const saveNotePatchIfNeeded = useCallback(async () => {
    if (!block || isCreateMode) return
    const newNote = note.trim() || null
    const oldNote = (block.note ?? '').trim() || null
    if (newNote === oldNote) return
    setSaving(true)
    try {
      await onSave({ note: newNote })
    } catch {
      /* parent shows error */
    } finally {
      setSaving(false)
    }
  }, [block, isCreateMode, note, onSave])

  useEffect(() => {
    if (!block || isCreateMode) return
    if (taskTypeId < 1) return
    if (taskTypeId === block.task_type_id) return
    clearNoteDebounce()
    let cancelled = false
    setSaving(true)
    void (async () => {
      try {
        await onSave({ task_type_id: taskTypeId })
      } catch {
        /* parent shows error */
      } finally {
        if (!cancelled) setSaving(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [block, isCreateMode, taskTypeId, block?.task_type_id, onSave, clearNoteDebounce])

  useEffect(() => {
    if (!block || isCreateMode) return
    const newNote = note.trim() || null
    const oldNote = (block.note ?? '').trim() || null
    if (newNote === oldNote) return
    clearNoteDebounce()
    noteDebounceRef.current = setTimeout(() => {
      noteDebounceRef.current = null
      void saveNotePatchIfNeeded()
    }, NOTE_DEBOUNCE_MS)
    return () => clearNoteDebounce()
  }, [note, block, isCreateMode, saveNotePatchIfNeeded, clearNoteDebounce])

  useEffect(() => {
    if (!isCreateMode) {
      draftCreateAttemptedRef.current = false
      return
    }
    if (!canSaveCreate || !onCreateFromDraft) {
      draftCreateAttemptedRef.current = false
      return
    }
    if (draftCreateAttemptedRef.current) return
    draftCreateAttemptedRef.current = true
    const payload = { task_type_id: taskTypeId, note: note.trim() || null }
    setSaving(true)
    void (async () => {
      try {
        await onCreateFromDraft(payload)
      } catch {
        draftCreateAttemptedRef.current = false
      } finally {
        setSaving(false)
      }
    })()
  }, [isCreateMode, canSaveCreate, onCreateFromDraft, taskTypeId, note])

  const flushNoteNow = useCallback(async () => {
    clearNoteDebounce()
    await saveNotePatchIfNeeded()
  }, [clearNoteDebounce, saveNotePatchIfNeeded])

  const handleComplete = useCallback(async () => {
    if (!onCompleteAsPlanned) return
    await flushNoteNow()
    setSaving(true)
    try {
      await onCompleteAsPlanned()
      onClose()
    } catch {
      /* parent shows error */
    } finally {
      setSaving(false)
    }
  }, [flushNoteNow, onClose, onCompleteAsPlanned])

  const handleDelete = useCallback(async () => {
    await flushNoteNow()
    setSaving(true)
    try {
      await onDelete()
      onClose()
    } catch {
      /* parent shows error */
    } finally {
      setSaving(false)
    }
  }, [flushNoteNow, onDelete, onClose])

  if (!block && !draft) return null

  const lane = block?.lane ?? draft!.lane
  const startMinute = block?.start_minute ?? draft!.start_minute
  const endMinute = block?.end_minute ?? draft!.end_minute

  const startLabel = formatMinuteLabel24(startMinute)
  const endLabel = formatMinuteLabel24(endMinute)

  const durationTotal = endMinute - startMinute
  const durationH = Math.floor(durationTotal / 60)
  const durationM = durationTotal % 60
  const durationLabel = `${durationH}h ${String(durationM).padStart(2, '0')}m`

  const lanePillColors =
    lane === 'planned'
      ? 'border-planned-border bg-planned-surface text-planned'
      : 'border-actual-border bg-actual-surface text-actual'

  const laneDotColor = lane === 'planned' ? 'bg-planned' : 'bg-actual'

  const formClassName =
    variant === 'sheet'
      ? 'flex max-h-[min(85vh,56rem)] flex-col gap-5 overflow-y-auto rounded-2xl bg-surface-container-lowest/90 p-5 shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-[20px] dark:bg-dark-surface-container-lowest/85 dark:shadow-[0_0_40px_rgba(0,0,0,0.25)]'
      : 'flex max-h-[min(85vh,56rem)] flex-col gap-5 overflow-y-auto rounded-2xl bg-surface-container-lowest/90 p-5 shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-[20px] dark:bg-dark-surface-container-lowest/85 dark:shadow-[0_0_40px_rgba(0,0,0,0.25)] lg:max-h-[calc(100vh-8rem)] lg:rounded-none lg:bg-transparent lg:p-5 lg:shadow-none lg:backdrop-blur-none'

  return (
    <div className={formClassName}>
      {/* Header: lane pill + duration pill + close */}
      <div className="flex shrink-0 items-center gap-2">
        <h2
          id="block-panel-title"
          className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10.5px] font-medium uppercase tracking-wider ${lanePillColors}`}
        >
          <span className={`inline-block h-1.5 w-1.5 rounded-full ${laneDotColor}`} />
          {isCreateMode ? 'New block' : lane === 'planned' ? 'Planned' : 'Actual'}
        </h2>
        <div className="ml-auto flex items-center gap-2">
          <span className="rounded-md bg-surface-container px-2 py-0.5 font-mono text-[10.5px] text-on-surface-variant">
            {durationLabel}
          </span>
          <button
            type="button"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container"
            aria-label="Close"
            onClick={() => onClose()}
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden>
              close
            </span>
          </button>
        </div>
      </div>

      {/* Time range headline */}
      <p className="font-headline text-4xl font-extralight tracking-tight tabular-nums text-on-surface">
        <span>{startLabel}</span>
        <span className="text-outline-variant"> – </span>
        <span>{endLabel}</span>
      </p>

      {/* Helper text */}
      <p className="max-w-xs font-body text-xs leading-relaxed text-on-surface-variant">
        {isCreateMode
          ? 'Pick a task type to create this block. Edits save as you make them.'
          : 'Drag the block edges on the timeline to adjust. Edits save as you make them.'}
      </p>

      {(block?.task ?? (draft?.task_id ? { id: draft.task_id, title: 'Selected Battle Plan task' } : null)) ? (
        <div className="rounded-xl border border-outline-variant/25 bg-surface-container-low px-3 py-2.5 dark:border-dark-outline-variant">
          <p className="font-label text-[10px] uppercase tracking-[0.12em] text-on-surface-variant">Battle Plan task</p>
          {block?.task ? (
            <Link className="mt-1 inline-flex items-center gap-1 text-sm font-medium text-on-surface hover:text-primary" to={`/battle-plan?task=${block.task.id}`}>
              {block.task.title}
              <span className="material-symbols-outlined text-[16px]" aria-hidden>open_in_new</span>
            </Link>
          ) : (
            <p className="mt-1 text-sm text-on-surface">Selected from Ready to Plan</p>
          )}
        </div>
      ) : null}

      {/* Task type */}
      <TaskTypePathCombobox
        label="Task type"
        taskTypes={taskTypes}
        valueTaskTypeId={taskTypeId}
        onSelectTaskTypeId={setTaskTypeId}
        onCreateTaskTypePath={onCreateTaskTypePath}
      />

      {/* Note */}
      <div>
        <label htmlFor="block-note" className="mb-1.5 block text-[11px] font-medium text-on-surface-variant">
          Note
        </label>
        <textarea
          id="block-note"
          rows={4}
          className="min-h-20 w-full rounded-xl border border-outline-variant/35 bg-surface px-3 py-2.5 font-body text-[13.5px] leading-relaxed text-on-surface placeholder:text-outline-variant/80 outline-none transition-colors focus:border-primary/40 focus:ring-1 focus:ring-primary/20 dark:border-dark-outline-variant dark:bg-dark-surface-container-lowest dark:text-dark-on-surface"
          placeholder="Optional"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          onBlur={() => void flushNoteNow()}
        />
      </div>

      {/* Action row */}
      <div className="mt-auto flex shrink-0 items-center">
        {!isCreateMode && (
          <button
            type="button"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-error transition-colors hover:bg-error-container/15"
            aria-label="Delete"
            title="Delete"
            onClick={() => void handleDelete()}
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden>
              delete
            </span>
          </button>
        )}
        <div className="flex-1" />
        {!isCreateMode && block?.lane === 'planned' && onCompleteAsPlanned && (
          <button
            type="button"
            disabled={!!hasLinkedActual || saving}
            className="inline-flex h-9 items-center gap-1.5 rounded-full bg-on-surface px-[18px] text-[13px] font-medium text-surface transition-colors hover:bg-on-surface/85 disabled:opacity-50 dark:bg-dark-on-surface dark:text-dark-surface dark:hover:bg-dark-on-surface/85"
            onClick={() => void handleComplete()}
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden>
              check
            </span>
            {hasLinkedActual ? 'Completed' : 'Mark complete'}
          </button>
        )}
      </div>
    </div>
  )
}
