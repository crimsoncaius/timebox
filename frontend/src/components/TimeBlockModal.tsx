import { useEffect } from 'react'
import type { BlockDraftPlacement, DayRead, TaskType, TimeBlock } from '../lib/api'
import { TimeBlockInspectorContent } from './TimeBlockInspectorContent'

/**
 * Mobile sheet for editing a time block. Desktop uses the persistent rail in TodayPage.
 */
export function TimeBlockModal({
  open,
  block,
  draft,
  day,
  taskTypes,
  onClose,
  onSave,
  onCreateFromDraft,
  onDelete,
  onRecordActualAsPlanned,
  onCreateTaskTypePath,
  onDirtyChange,
  blockDragActive = false,
}: {
  open: boolean
  block: TimeBlock | null
  draft: BlockDraftPlacement | null
  day: DayRead
  taskTypes: TaskType[]
  onClose: () => void
  onSave: (patch: { task_type_id?: number; note?: string | null }) => Promise<void>
  onCreateFromDraft?: (payload: { task_type_id: number; note: string | null }) => Promise<void>
  onDelete: () => Promise<void>
  onRecordActualAsPlanned?: () => Promise<void>
  onCreateTaskTypePath: (path: string) => Promise<TaskType>
  onDirtyChange?: (dirty: boolean) => void
  /** Ignore pointer events so a timeline drag release does not hit sheet controls. */
  blockDragActive?: boolean
}) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open || (!block && !draft)) return null

  return (
    <aside
      role="dialog"
      aria-modal="true"
      aria-labelledby="block-panel-title"
      data-inspector="sheet"
      className={`w-full shrink-0 bg-surface-container-low dark:bg-dark-surface-container-low lg:hidden${blockDragActive ? ' pointer-events-none' : ''}`}
    >
      <TimeBlockInspectorContent
        variant="sheet"
        block={block}
        draft={draft}
        day={day}
        taskTypes={taskTypes}
        onClose={onClose}
        onSave={onSave}
        onCreateFromDraft={onCreateFromDraft}
        onDelete={onDelete}
        onRecordActualAsPlanned={onRecordActualAsPlanned}
        onCreateTaskTypePath={onCreateTaskTypePath}
        onDirtyChange={onDirtyChange}
      />
    </aside>
  )
}
