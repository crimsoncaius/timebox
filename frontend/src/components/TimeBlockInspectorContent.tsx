import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { BlockDraftPlacement, DayRead, TaskType, TimeBlock } from '../lib/api'
import { formatMinuteLabel24 } from '../lib/time'
import { TaskTypePathCombobox } from './TaskTypePathCombobox'
import { Link } from 'react-router-dom'

export type TimeBlockInspectorVariant = 'rail' | 'sheet'

const NOTE_DEBOUNCE_MS = 450
const NAME_DEBOUNCE_MS = 450

/**
 * Shared form for editing a time block in the desktop inspector rail or mobile sheet.
 * Start/end times are display-only; adjust duration on the timeline.
 * Task type, Block Name, and Note persist automatically.
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
  onSave: (patch: { task_type_id?: number; name?: string | null; note?: string | null }) => Promise<void>
  onCreateFromDraft?: (payload: { task_type_id?: number; name: string | null; note: string | null }) => Promise<void>
  onDelete: () => Promise<void>
  onRecordActualAsPlanned?: () => Promise<void>
  onCreateTaskTypePath: (path: string) => Promise<TaskType>
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [taskTypeId, setTaskTypeId] = useState(() => block?.task_type_id ?? draft?.task_type_id ?? 0)
  const [name, setName] = useState(() => block?.name ?? '')
  const [note, setNote] = useState(() => block?.note ?? '')
  const [saving, setSaving] = useState(false)

  const isCreateMode = draft != null && block == null
  const lane = block?.lane ?? draft?.lane
  const isNameEditable = lane === 'planned' || lane === 'actual'
  const noteDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const nameDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const draftCreateAttemptedRef = useRef(false)
  const editedFieldsRef = useRef({ name: false, note: false })

  const clearNoteDebounce = useCallback(() => {
    if (noteDebounceRef.current) {
      clearTimeout(noteDebounceRef.current)
      noteDebounceRef.current = null
    }
  }, [])

  const clearNameDebounce = useCallback(() => {
    if (nameDebounceRef.current) {
      clearTimeout(nameDebounceRef.current)
      nameDebounceRef.current = null
    }
  }, [])

  // A different selection starts a new editing session, including equal IDs in different lanes.
  useLayoutEffect(() => {
    editedFieldsRef.current = { name: false, note: false }
    setName(block?.name ?? '')
    setNote(block?.note ?? '')
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset only when the selected Block or draft changes.
  }, [block?.id, block?.lane, draft])

  // Untouched responsive peers follow server snapshots. Once edited, a field owns its
  // draft for this selection: a response for another field (or an older save) may be stale.
  useLayoutEffect(() => {
    setTaskTypeId(block?.task_type_id ?? draft?.task_type_id ?? 0)
    if (!editedFieldsRef.current.name) setName(block?.name ?? '')
    if (!editedFieldsRef.current.note) setNote(block?.note ?? '')
  }, [block?.id, block?.lane, block?.task_type_id, block?.name, block?.note, draft])

  const dirty = useMemo(() => {
    if (isCreateMode) {
      return taskTypeId !== 0 || name.trim() !== '' || note.trim() !== ''
    }
    if (block) {
      const newNote = note.trim() || null
      const oldNote = (block.note ?? '').trim() || null
      const newName = name.trim() || null
      const oldName = (block.name ?? '').trim() || null
      return taskTypeId !== block.task_type_id || newName !== oldName || newNote !== oldNote
    }
    return false
  }, [block, isCreateMode, name, note, taskTypeId])

  useEffect(() => {
    onDirtyChange?.(dirty)
  }, [dirty, onDirtyChange])

  const canSaveCreate =
    isCreateMode &&
    (isNameEditable || (taskTypeId > 0 && taskTypes.some((t) => t.id === taskTypeId))) &&
    !!onCreateFromDraft

  const saveNamePatchIfNeeded = useCallback(async () => {
    if (!block || isCreateMode || !isNameEditable) return
    const newName = name.trim() || null
    const oldName = (block.name ?? '').trim() || null
    if (newName === oldName) return
    setSaving(true)
    try {
      await onSave({ name: newName })
      setName((current) => current === name ? newName ?? '' : current)
    } catch {
      /* parent shows error */
    } finally {
      setSaving(false)
    }
  }, [block, isCreateMode, isNameEditable, name, onSave])

  const saveNotePatchIfNeeded = useCallback(async () => {
    if (!block || isCreateMode) return
    const newNote = note.trim() || null
    const oldNote = (block.note ?? '').trim() || null
    if (newNote === oldNote) return
    setSaving(true)
    try {
      await onSave({ note: newNote })
      setNote((current) => current === note ? newNote ?? '' : current)
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
    if (!block || isCreateMode || !isNameEditable) return
    const newName = name.trim() || null
    const oldName = (block.name ?? '').trim() || null
    if (newName === oldName) return
    clearNameDebounce()
    nameDebounceRef.current = setTimeout(() => {
      nameDebounceRef.current = null
      void saveNamePatchIfNeeded()
    }, NAME_DEBOUNCE_MS)
    return () => clearNameDebounce()
  }, [name, block, isCreateMode, isNameEditable, saveNamePatchIfNeeded, clearNameDebounce])

  useEffect(() => {
    if (!isCreateMode) {
      draftCreateAttemptedRef.current = false
      return
    }
    const selectedValidType = taskTypeId > 0 && taskTypes.some((type) => type.id === taskTypeId)
    if (!canSaveCreate || !onCreateFromDraft || !selectedValidType) {
      draftCreateAttemptedRef.current = false
      return
    }
    if (draftCreateAttemptedRef.current) return
    draftCreateAttemptedRef.current = true
    const payload = { task_type_id: taskTypeId, name: name.trim() || null, note: note.trim() || null }
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
  }, [isCreateMode, canSaveCreate, onCreateFromDraft, taskTypeId, taskTypes, name, note])

  const flushNoteNow = useCallback(async () => {
    clearNoteDebounce()
    await saveNotePatchIfNeeded()
  }, [clearNoteDebounce, saveNotePatchIfNeeded])

  const flushNameNow = useCallback(async () => {
    clearNameDebounce()
    await saveNamePatchIfNeeded()
  }, [clearNameDebounce, saveNamePatchIfNeeded])

  const flushTextNow = useCallback(async () => {
    await Promise.all([flushNameNow(), flushNoteNow()])
  }, [flushNameNow, flushNoteNow])

  const handleActualAction = useCallback(async (action: () => Promise<void>) => {
    await flushTextNow()
    setSaving(true)
    try {
      await action()
    } catch {
      /* parent shows error */
    } finally {
      setSaving(false)
    }
  }, [flushTextNow])

  const handleDelete = useCallback(async () => {
    if (!window.confirm('Permanently delete this time block? This cannot be undone.')) return
    await flushTextNow()
    setSaving(true)
    try {
      await onDelete()
      onClose()
    } catch {
      /* parent shows error */
    } finally {
      setSaving(false)
    }
  }, [flushTextNow, onDelete, onClose])

  if (!block && !draft) return null

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
      {block?.lane === 'planned' && !!block.actual_block_ids?.length ? <p>{block.actual_block_ids.length} linked Actual Blocks · {Math.floor(block.actual_duration_minutes ?? 0)}m recorded</p> : null}
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
          ? isNameEditable
            ? 'Add a Name, choose a Task Type if useful, then create the block.'
            : 'Pick a Task Type to create this block. Edits save as you make them.'
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

      {isNameEditable ? (
        <div>
          <label htmlFor="block-name" className="mb-1.5 block text-[11px] font-medium text-on-surface-variant">
            Name
          </label>
          <input
            id="block-name"
            type="text"
            maxLength={500}
            className="w-full rounded-xl border border-outline-variant/35 bg-surface px-3 py-2.5 font-body text-[13.5px] text-on-surface placeholder:text-outline-variant/80 outline-none transition-colors focus:border-primary/40 focus:ring-1 focus:ring-primary/20 dark:border-dark-outline-variant dark:bg-dark-surface-container-lowest dark:text-dark-on-surface"
            placeholder="Optional"
            value={name}
            onChange={(event) => {
              editedFieldsRef.current.name = true
              setName(event.target.value)
            }}
            onBlur={() => void flushNameNow()}
          />
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
          onChange={(e) => {
            editedFieldsRef.current.note = true
            setNote(e.target.value)
          }}
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
        {isCreateMode && isNameEditable && taskTypeId === 0 ? (
          <button
            type="button"
            disabled={!canSaveCreate || saving}
            className="rounded-xl bg-primary px-4 py-2.5 text-sm font-medium text-on-primary disabled:opacity-40"
            onClick={() => {
              if (!onCreateFromDraft) return
              draftCreateAttemptedRef.current = true
              setSaving(true)
              void onCreateFromDraft({ name: name.trim() || null, note: note.trim() || null })
                .catch(() => { draftCreateAttemptedRef.current = false })
                .finally(() => setSaving(false))
            }}
          >
            Create block
          </button>
        ) : null}
      </div>
    </div>
  )
}
