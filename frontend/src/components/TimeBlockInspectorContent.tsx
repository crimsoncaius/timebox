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
  taskTypes,
  variant,
  onClose,
  onSave,
  onCreateFromDraft,
  onDelete,
  onRecordActualAsPlanned,
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
  onRecordActualAsPlanned?: () => Promise<void>
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

  /** Keep both responsive inspector instances aligned with the authoritative block. */
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
    // Note remains locally editable; only an authoritative task-type change for the same block is synchronized.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- avoid wiping a pending note on unrelated block refreshes.
  }, [block?.id, block?.task_type_id, draft])

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

  const selectTaskType = useCallback((nextTaskTypeId: number) => {
    if (nextTaskTypeId === taskTypeId) return
    setTaskTypeId(nextTaskTypeId)
    if (!block || isCreateMode || nextTaskTypeId < 1) return
    clearNoteDebounce()
    setSaving(true)
    void (async () => {
      try {
        await onSave({ task_type_id: nextTaskTypeId })
      } catch {
        setTaskTypeId(block.task_type_id)
        /* parent shows error */
      } finally {
        setSaving(false)
      }
    })()
  }, [block, isCreateMode, taskTypeId, onSave, clearNoteDebounce])

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

  const handleActualAction = useCallback(async (action: () => Promise<void>) => {
    await flushNoteNow()
    setSaving(true)
    try {
      await action()
    } catch {
      /* parent shows error */
    } finally {
      setSaving(false)
    }
  }, [flushNoteNow])

  const handleDelete = useCallback(async () => {
    if (!window.confirm('Permanently delete this time block? This cannot be undone.')) return
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
        onSelectTaskTypeId={selectTaskType}
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

      {!isCreateMode && block?.lane === 'planned' ? (
        <section aria-label="Actual time actions" className="grid gap-2 rounded-xl border border-outline-variant/25 bg-surface-container-low p-3 dark:border-dark-outline-variant">
          {onRecordActualAsPlanned ? <button type="button" disabled={saving} onClick={() => void handleActualAction(onRecordActualAsPlanned)} className="rounded-xl border border-outline-variant/40 px-4 py-3 text-sm font-medium disabled:opacity-40">Record Actual as planned</button> : null}
        </section>
      ) : null}

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
      </div>
    </div>
  )
}
