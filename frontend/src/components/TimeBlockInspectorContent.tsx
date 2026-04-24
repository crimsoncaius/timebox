import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { BlockDraftPlacement, DayRead, TaskType, TimeBlock } from '../lib/api'
import { formatMinuteLabel24 } from '../lib/time'
import { TaskTypePathCombobox } from './TaskTypePathCombobox'

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
  const [taskTypeId, setTaskTypeId] = useState(() => block?.task_type_id ?? 0)
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
      setTaskTypeId(0)
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

  const laneLabel = lane === 'planned' ? 'Planned' : 'Actual'
  const startLabel = formatMinuteLabel24(startMinute)
  const endLabel = formatMinuteLabel24(endMinute)

  const formClassName =
    variant === 'sheet'
      ? 'flex max-h-[min(85vh,56rem)] flex-col gap-4 overflow-y-auto rounded-2xl bg-surface-container-lowest/90 px-4 pb-6 pt-6 shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-[20px] dark:bg-stone-950/85 dark:shadow-[0_0_40px_rgba(0,0,0,0.25)]'
      : 'flex max-h-[min(85vh,56rem)] flex-col gap-4 overflow-y-auto rounded-2xl bg-surface-container-lowest/90 px-4 pb-6 pt-6 shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-[20px] dark:bg-stone-950/85 dark:shadow-[0_0_40px_rgba(0,0,0,0.25)] lg:max-h-[calc(100vh-8rem)] lg:rounded-none lg:bg-transparent lg:px-0 lg:pb-6 lg:pt-6 lg:shadow-none lg:backdrop-blur-none'

  return (
    <div className={formClassName}>
      <div className="flex shrink-0 items-start justify-between gap-4">
        <div>
          <h2
            id="block-panel-title"
            className="font-headline text-sm font-light tracking-wide text-on-surface-variant">
            {isCreateMode ? 'New block' : 'Block details'}
          </h2>
          <p className="mt-1 font-headline text-xl font-extralight tracking-tight text-on-surface">
            {laneLabel}
          </p>
          <p className="mt-1 font-headline text-xl font-light tabular-nums tracking-tight text-on-surface">
            {startLabel}–{endLabel}
          </p>
          <p className="mt-1.5 max-w-xs font-body text-xs leading-relaxed text-on-surface-variant">
            {isCreateMode
              ? 'Pick a task type to create this block. Notes save as you type. Click the timeline to move it.'
              : 'Adjust start and end on the timeline by dragging the block edges. Task type and notes save automatically.'}
          </p>
        </div>
        <button
          type="button"
          className="shrink-0 rounded-full p-2 text-on-surface-variant transition-colors hover:bg-surface-container-high"
          aria-label="Close"
          onClick={() => onClose()}
        >
          <span className="material-symbols-outlined text-[22px]" aria-hidden>
            close
          </span>
        </button>
      </div>

      <div className="flex gap-2.5">
        <div className="min-w-0 flex-1 rounded-xl bg-surface-container-low px-2.5 py-2 dark:bg-stone-800/80">
          <p className="font-headline text-[10px] text-on-surface-variant">Start</p>
          <p className="mt-0.5 font-headline text-lg font-light tabular-nums text-on-surface">{startLabel}</p>
        </div>
        <div className="min-w-0 flex-1 rounded-xl bg-surface-container-low px-2.5 py-2 dark:bg-stone-800/80">
          <p className="font-headline text-[10px] text-on-surface-variant">End</p>
          <p className="mt-0.5 font-headline text-lg font-light tabular-nums text-on-surface">{endLabel}</p>
        </div>
      </div>

      <TaskTypePathCombobox
        label="Task type"
        taskTypes={taskTypes}
        valueTaskTypeId={taskTypeId}
        onSelectTaskTypeId={setTaskTypeId}
        onCreateTaskTypePath={onCreateTaskTypePath}
      />

      <div>
        <label htmlFor="block-note" className="mb-0.5 block font-body text-xs text-on-surface-variant">
          Note
        </label>
        <textarea
          id="block-note"
          rows={4}
          className="min-h-24 w-full rounded-xl border border-outline-variant/15 bg-surface px-3 py-2.5 font-body text-sm leading-relaxed text-on-surface placeholder:text-outline-variant/80 outline-none transition-colors focus:border-primary/40 focus:ring-1 focus:ring-primary/20"
          placeholder="Optional"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          onBlur={() => void flushNoteNow()}
        />
      </div>

      <div className="mt-auto flex shrink-0 flex-wrap items-center justify-end gap-2">
        {!isCreateMode && block?.lane === 'planned' && onCompleteAsPlanned && (
          <button
            type="button"
            disabled={!!hasLinkedActual || saving}
            className="rounded-full border border-outline-variant/15 bg-tertiary-container/50 px-4 py-2 text-sm text-on-surface transition-colors hover:bg-surface-container-high disabled:opacity-50 dark:bg-stone-800/60"
            onClick={() => void handleComplete()}
          >
            {hasLinkedActual ? 'Completed' : 'Complete'}
          </button>
        )}
        {!isCreateMode && (
          <button
            type="button"
            className="shrink-0 rounded-full p-2 text-error transition-colors hover:bg-error-container/20"
            aria-label="Delete"
            title="Delete"
            onClick={() => void handleDelete()}
          >
            <span className="material-symbols-outlined text-[22px]" aria-hidden>
              delete
            </span>
          </button>
        )}
      </div>
    </div>
  )
}
