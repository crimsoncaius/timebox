import { DragDropProvider, PointerSensor, useDraggable, type DragEndEvent } from '@dnd-kit/react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { DayCalendarPopover } from '../../components/DayCalendarPopover'
import {
  DayTimeline,
  PLANNED_LANE_DROP_ID,
  READY_TASK_DRAG_TYPE,
} from '../../components/DayTimeline'
import { Layout } from '../../components/Layout'
import { TimeBlockInspectorContent } from '../../components/TimeBlockInspectorContent'
import { TimeBlockModal } from '../../components/TimeBlockModal'
import { api, type BattleTask, type BlockDraftPlacement, type BlockLane, type DayRead, type TaskType } from '../../lib/api'
import {
  addDaysIso,
  minuteFromPointerYInVisibleLane,
  SLOT_MINUTES,
  TIMELINE_SLOT_HEIGHT_PX,
  visibleMinuteRange,
} from '../../lib/time'

function formatDisplayDate(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  if (!y || !m || !d) return isoDate
  const dt = new Date(Date.UTC(y, m - 1, d))
  return dt.toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

function confirmDiscardUnsaved(): boolean {
  return window.confirm('Discard unsaved changes?')
}

export function TodayPage() {
  const { date } = useParams<{ date: string }>()
  const navigate = useNavigate()
  const [day, setDay] = useState<DayRead | null>(null)
  const [taskTypes, setTaskTypes] = useState<TaskType[]>([])
  const [battleTasks, setBattleTasks] = useState<BattleTask[]>([])
  const [planningTaskId, setPlanningTaskId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedBlockId, setSelectedBlockId] = useState<number | null>(null)
  const [draft, setDraft] = useState<BlockDraftPlacement | null>(null)
  const [inspectorDirty, setInspectorDirty] = useState(false)
  const [blockDragActive, setBlockDragActive] = useState(false)
  const timelineRef = useRef<HTMLDivElement>(null)
  const draftCommitInFlightRef = useRef(false)
  const planningTaskInFlightRef = useRef(false)
  const [planningTaskBusyId, setPlanningTaskBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    if (!date) return
    setLoading(true)
    setError(null)
    try {
      const [d, tt, battle] = await Promise.all([
        api.getDay(date),
        api.listTaskTypes(),
        api.listBattleTasks('active').catch(() => null),
      ])
      setDay(d)
      setTaskTypes(tt)
      setBattleTasks(battle?.items ?? [])
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load day')
    } finally {
      setLoading(false)
    }
  }, [date])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    setSelectedBlockId(null)
    setDraft(null)
    setInspectorDirty(false)
  }, [date])

  const selectedBlock = useMemo(() => {
    if (selectedBlockId == null || !day) return null
    return day.time_blocks.find((b) => b.id === selectedBlockId) ?? null
  }, [day, selectedBlockId])

  useEffect(() => {
    if (selectedBlockId != null && day && !day.time_blocks.some((b) => b.id === selectedBlockId)) {
      setSelectedBlockId(null)
    }
  }, [day, selectedBlockId])

  const tryDiscardIfNeeded = useCallback(() => {
    if (!inspectorDirty) return true
    return confirmDiscardUnsaved()
  }, [inspectorDirty])

  const tryClosePanel = useCallback(() => {
    if (!tryDiscardIfNeeded()) return
    setSelectedBlockId(null)
    setDraft(null)
  }, [tryDiscardIfNeeded])

  const resolvePlanningTaskType = useCallback(
    async (task: BattleTask): Promise<number> => {
      if (task.task_type_id != null) return task.task_type_id

      const findUnspecified = (types: TaskType[]) =>
        types.find((type) => type.name.trim().toLowerCase() === 'unspecified')

      const current = findUnspecified(taskTypes)
      if (current) return current.id

      const refreshed = await api.listTaskTypes()
      setTaskTypes(refreshed)
      const existing = findUnspecified(refreshed)
      if (existing) return existing.id

      try {
        const created = await api.createTaskType({ name: 'unspecified' })
        setTaskTypes((types) => [...types.filter((type) => type.id !== created.id), created])
        return created.id
      } catch (createError) {
        // Another client may have created the unique fallback between our GET and POST.
        const afterConflict = await api.listTaskTypes()
        setTaskTypes(afterConflict)
        const concurrent = findUnspecified(afterConflict)
        if (concurrent) return concurrent.id
        throw createError
      }
    },
    [taskTypes],
  )

  const planReadyTaskAt = useCallback(
    async (taskId: number, startMinute: number) => {
      if (!date || !day || planningTaskInFlightRef.current) return
      const task = battleTasks
        .flatMap((item) => [item, ...item.subtasks])
        .find((item) => item.id === taskId && item.ready_to_plan)
      if (!task) return

      const { start: visibleStart, end: visibleEnd } = visibleMinuteRange(day)
      const start = Math.max(visibleStart, Math.min(startMinute, visibleEnd - SLOT_MINUTES))
      const end = start + SLOT_MINUTES
      const overlaps = day.time_blocks.some(
        (block) =>
          block.lane === 'planned' && start < block.end_minute && end > block.start_minute,
      )
      if (overlaps) {
        setError('That time is already planned.')
        return
      }

      planningTaskInFlightRef.current = true
      setPlanningTaskBusyId(task.id)
      setError(null)
      try {
        const taskTypeId = await resolvePlanningTaskType(task)
        const next = await api.createBlock(date, {
          lane: 'planned',
          task_type_id: taskTypeId,
          task_id: task.id,
          start_minute: start,
          end_minute: end,
        })
        setDay(next)
        setPlanningTaskId(null)
        setDraft(null)
        setSelectedBlockId(null)
        setBattleTasks((items) =>
          items.map((item) => ({
            ...item,
            ready_to_plan: item.id === task.id ? false : item.ready_to_plan,
            subtasks: item.subtasks.map((subtask) =>
              subtask.id === task.id ? { ...subtask, ready_to_plan: false } : subtask,
            ),
          })),
        )
        const refreshed = await api.listBattleTasks('active').catch(() => null)
        if (refreshed) setBattleTasks(refreshed.items)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to plan task')
      } finally {
        planningTaskInFlightRef.current = false
        setPlanningTaskBusyId(null)
      }
    },
    [battleTasks, date, day, resolvePlanningTaskType],
  )

  const onLaneSlotClick = useCallback(
    (lane: BlockLane, startMin: number, endMin: number) => {
      if (!tryDiscardIfNeeded()) return
      const planningTask = battleTasks
        .flatMap((task) => [task, ...task.subtasks])
        .find((task) => task.id === planningTaskId)
      if (lane === 'planned' && planningTask) {
        void planReadyTaskAt(planningTask.id, startMin)
        return
      }
      setDraft({
        lane,
        start_minute: startMin,
        end_minute: endMin,
        task_id: lane === 'planned' ? planningTask?.id ?? null : null,
        task_type_id: lane === 'planned' ? planningTask?.task_type_id ?? null : null,
      })
      setSelectedBlockId(null)
    },
    [battleTasks, planReadyTaskAt, planningTaskId, tryDiscardIfNeeded],
  )

  const onReadyTaskDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { source, target, position } = event.operation
      if (
        event.canceled ||
        source?.type !== READY_TASK_DRAG_TYPE ||
        target?.id !== PLANNED_LANE_DROP_ID
      ) {
        return
      }
      const taskId = Number(source.data.taskId)
      const laneElement = target.element
      if (!Number.isFinite(taskId) || !laneElement || !day) return
      const { start: visibleStart, end: visibleEnd } = visibleMinuteRange(day)
      const pointerY = position.current.y - laneElement.getBoundingClientRect().top
      const start = minuteFromPointerYInVisibleLane(
        pointerY,
        visibleStart,
        visibleEnd,
        TIMELINE_SLOT_HEIGHT_PX,
      )
      void planReadyTaskAt(taskId, start)
    },
    [day, planReadyTaskAt],
  )

  const onDraftTimeChange = useCallback((startMin: number, endMin: number) => {
    setDraft((d) => (d ? { ...d, start_minute: startMin, end_minute: endMin } : null))
  }, [])

  useEffect(() => {
    if (draft == null && selectedBlockId == null) return
    const onPointerDown = (e: PointerEvent) => {
      const node = e.target
      if (!(node instanceof Node)) return
      if (timelineRef.current?.contains(node)) return
      const el = node instanceof Element ? node : node.parentElement
      if (el?.closest('[role="dialog"]')) return
      if (el?.closest('[data-inspector]')) return
      tryClosePanel()
    }
    document.addEventListener('pointerdown', onPointerDown, true)
    return () => document.removeEventListener('pointerdown', onPointerDown, true)
  }, [draft, selectedBlockId, tryClosePanel])

  /** Desktop: Escape clears selection (mobile sheet uses TimeBlockModal's Escape handler). */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      if (window.matchMedia('(max-width: 1023px)').matches) return
      if (selectedBlock == null && draft == null) return
      tryClosePanel()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [draft, selectedBlock, tryClosePanel])

  const commitDraft = useCallback(
    async (payload: { task_type_id: number; note: string | null }) => {
      if (!date || !draft || draftCommitInFlightRef.current) return
      draftCommitInFlightRef.current = true
      setError(null)
      try {
        const next = await api.createBlock(date, {
          lane: draft.lane,
          task_type_id: payload.task_type_id,
          task_id: draft.task_id ?? null,
          note: payload.note ?? undefined,
          start_minute: draft.start_minute,
          end_minute: draft.end_minute,
        })
        setDay(next)
        const created = next.time_blocks.find(
          (b) =>
            b.lane === draft.lane &&
            b.start_minute === draft.start_minute &&
            b.end_minute === draft.end_minute &&
            b.task_type_id === payload.task_type_id,
        )
        if (draft.task_id) {
          const refreshed = await api.listBattleTasks('active')
          setBattleTasks(refreshed.items)
          setPlanningTaskId(null)
        }
        setDraft(null)
        if (created) setSelectedBlockId(created.id)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to create block')
        throw e
      } finally {
        draftCommitInFlightRef.current = false
      }
    },
    [date, draft],
  )

  const patchBlock = useCallback(
    async (
      blockId: number,
      patch: {
        task_type_id?: number
        task_id?: number | null
        note?: string | null
        start_minute?: number
        end_minute?: number
      },
    ) => {
      if (!date) return
      setError(null)
      try {
        const next = await api.patchBlock(date, blockId, patch)
        setDay(next)
      } catch (e) {
        const msg = e instanceof Error ? e.message : 'Failed to update block'
        setError(msg)
        throw e
      }
    },
    [date],
  )

  const deleteBlock = useCallback(
    async (blockId: number) => {
      if (!date) return
      setError(null)
      try {
        const next = await api.deleteBlock(date, blockId)
        setDay(next)
      } catch (e) {
        const msg = e instanceof Error ? e.message : 'Failed to delete block'
        setError(msg)
        throw e
      }
    },
    [date],
  )

  const completeBlockAsPlanned = useCallback(
    async (blockId: number) => {
      if (!date) return
      setError(null)
      try {
        const next = await api.completeBlockAsPlanned(date, blockId)
        setDay(next)
      } catch (e) {
        const msg = e instanceof Error ? e.message : 'Failed to complete block'
        setError(msg)
        throw e
      }
    },
    [date],
  )

  const createTaskTypePath = useCallback(async (name: string) => {
    setError(null)
    try {
      const created = await api.createTaskType({ name })
      const nextTaskTypes = await api.listTaskTypes()
      setTaskTypes(nextTaskTypes)
      return created
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Failed to create task type'
      setError(msg)
      throw e
    }
  }, [])

  const onBlockClick = useCallback(
    (blockId: number): boolean => {
      if (!tryDiscardIfNeeded()) return false
      setDraft(null)
      setSelectedBlockId(blockId)
      return true
    },
    [tryDiscardIfNeeded],
  )

  if (!date) {
    return (
      <Layout>
        <p className="text-error">Missing date in URL.</p>
      </Layout>
    )
  }

  if (loading) {
    return (
      <Layout>
        <p className="text-on-surface-variant">Loading…</p>
      </Layout>
    )
  }

  if (!day) {
    return (
      <Layout>
        <p className="text-error">{error ?? 'Failed to load day.'}</p>
      </Layout>
    )
  }

  const inspectorSharedProps = {
    day,
    taskTypes,
    onClose: tryClosePanel,
    onSave: (patch: { task_type_id?: number; note?: string | null }) => {
      if (!selectedBlock) return Promise.resolve()
      return patchBlock(selectedBlock.id, patch)
    },
    onCreateFromDraft: commitDraft,
    onDelete: () => {
      if (!selectedBlock) return Promise.resolve()
      return deleteBlock(selectedBlock.id)
    },
    onCompleteAsPlanned:
      selectedBlock?.lane === 'planned'
        ? () => {
            if (!selectedBlock) return Promise.resolve()
            return completeBlockAsPlanned(selectedBlock.id)
          }
        : undefined,
    onCreateTaskTypePath: createTaskTypePath,
    onDirtyChange: setInspectorDirty,
  }

  const mobileSheetOpen = selectedBlock != null || draft != null
  const readyTasks = battleTasks
    .flatMap((task) => [task, ...task.subtasks])
    .filter((task) => task.ready_to_plan)
  const planningTask = readyTasks.find((task) => task.id === planningTaskId) ?? null

  return (
    <Layout mainClassName="w-full max-w-none bg-transparent px-6 py-12 lg:px-8 xl:px-10 dark:bg-dark-surface">
      <DragDropProvider onDragEnd={onReadyTaskDragEnd}>
      <div className="flex flex-col gap-8 lg:flex-row lg:gap-0 lg:items-stretch">
        <div className="min-w-0 min-h-0 flex-1 lg:pr-4">
          <span data-testid="day-date" className="sr-only">
            {day.date}
          </span>
          <section className="mb-16">
            <div className="flex flex-wrap items-end justify-between gap-6">
              <div>
                <h1 className="mb-2 font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
                  {formatDisplayDate(day.date)}
                </h1>
                <p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
                  Timezone {day.meta.timezone}.
                </p>
              </div>
              <div
                className="flex flex-wrap items-center gap-2"
                data-testid="day-nav"
              >
                <button
                  type="button"
                  className="rounded-full border border-outline-variant/15 px-3 py-1.5 font-headline text-sm text-on-surface transition-colors hover:bg-surface-container-high dark:border-dark-outline-variant dark:text-dark-on-surface dark:hover:bg-dark-surface-container-high"
                  aria-label="Previous day"
                  onClick={() => navigate(`/day/${addDaysIso(day.date, -1)}`)}
                >
                  ← Prev
                </button>
                <DayCalendarPopover
                  value={day.date}
                  todayIso={day.meta.today}
                  onSelect={(iso) => navigate(`/day/${iso}`)}
                />
                <button
                  type="button"
                  className="rounded-full border border-outline-variant/15 px-3 py-1.5 font-headline text-sm text-on-surface transition-colors hover:bg-surface-container-high dark:border-dark-outline-variant dark:text-dark-on-surface dark:hover:bg-dark-surface-container-high"
                  aria-label="Next day"
                  onClick={() => navigate(`/day/${addDaysIso(day.date, 1)}`)}
                >
                  Next →
                </button>
              </div>
            </div>
          </section>

          {taskTypes.length === 0 && (
            <div className="mb-6 rounded-xl border border-outline-variant/30 bg-surface-container-low/80 px-4 py-3 text-sm text-on-surface-variant">
              No task types yet.{' '}
              <Link to="/task-types" className="text-primary underline">
                Create task types
              </Link>{' '}
              before adding blocks to the timeline.
            </div>
          )}

          {error && (
            <div className="mb-6 rounded-xl border border-error-container bg-error-container/20 px-4 py-3 text-sm text-on-error-container">
              {error}
            </div>
          )}

          <div className="mb-6 lg:hidden">
            <ReadyToPlanDrawer
              tasks={readyTasks}
              selectedTaskId={planningTaskId}
              dragInstance="mobile"
              busyTaskId={planningTaskBusyId}
              onSelect={(taskId) => {
                setPlanningTaskId(taskId)
                setDraft(null)
                setSelectedBlockId(null)
              }}
            />
          </div>

          {planningTask ? (
            <p className="mb-3 rounded-xl bg-primary/8 px-4 py-2.5 text-sm text-on-surface">
              <strong>{planningTask.title}</strong> is selected. Choose an open slot in the <strong>Planned</strong> lane.
            </p>
          ) : null}

          <section className="overflow-x-auto pb-24">
            <DayTimeline
              ref={timelineRef}
              day={day}
              readOnly={false}
              draft={draft}
              selectedBlockId={selectedBlockId}
              onLaneSlotClick={onLaneSlotClick}
              onDraftTimeChange={onDraftTimeChange}
              onPatchBlock={patchBlock}
              onBlockClick={(blockId) => onBlockClick(blockId)}
              onCompleteBlockAsPlanned={completeBlockAsPlanned}
              onBlockDragSessionChange={setBlockDragActive}
            />
          </section>
        </div>

        {/* Desktop: persistent inspector rail */}
        <div
          className="hidden w-full shrink-0 lg:block lg:w-[min(28rem,100%)] lg:max-w-md lg:pl-6"
          data-testid="day-inspector-rail"
        >
          <aside
            role="complementary"
            aria-label="Block details"
            data-inspector="rail"
            className={`sticky top-32 mt-0 max-h-[calc(100dvh-8.5rem)] w-full overflow-y-auto bg-surface-container-low dark:bg-dark-surface-container-low${blockDragActive ? ' pointer-events-none' : ''}`}
          >
            {selectedBlock == null && draft == null ? (
              <ReadyToPlanDrawer
                tasks={readyTasks}
                selectedTaskId={planningTaskId}
                dragInstance="desktop"
                busyTaskId={planningTaskBusyId}
                onSelect={(taskId) => {
                  setPlanningTaskId(taskId)
                  setDraft(null)
                  setSelectedBlockId(null)
                }}
              />
            ) : (
              <div className="transition-opacity duration-150">
                <TimeBlockInspectorContent
                  key={
                    selectedBlock != null
                      ? `block-${selectedBlock.id}`
                      : draft != null
                        ? `draft-${draft.lane}-${draft.start_minute}-${draft.end_minute}`
                        : 'none'
                  }
                  variant="rail"
                  block={selectedBlock}
                  draft={draft}
                  {...inspectorSharedProps}
                />
              </div>
            )}
          </aside>
        </div>

        {/* Mobile: sheet below timeline */}
        <div className="lg:hidden w-full">
          <TimeBlockModal
            key={
              selectedBlock != null
                ? `block-${selectedBlock.id}`
                : draft != null
                  ? `draft-${draft.lane}-${draft.start_minute}-${draft.end_minute}`
                  : 'none'
            }
            open={mobileSheetOpen}
            block={selectedBlock}
            draft={draft}
            day={day}
            taskTypes={taskTypes}
            onClose={tryClosePanel}
            onSave={inspectorSharedProps.onSave}
            onCreateFromDraft={commitDraft}
            onDelete={inspectorSharedProps.onDelete}
            onCompleteAsPlanned={inspectorSharedProps.onCompleteAsPlanned}
            onCreateTaskTypePath={createTaskTypePath}
            onDirtyChange={setInspectorDirty}
            blockDragActive={blockDragActive}
          />
        </div>
      </div>
      </DragDropProvider>
    </Layout>
  )
}

function ReadyToPlanDrawer({ tasks, selectedTaskId, dragInstance, busyTaskId, onSelect }: {
  tasks: BattleTask[]
  selectedTaskId: number | null
  dragInstance: 'mobile' | 'desktop'
  busyTaskId: number | null
  onSelect: (taskId: number | null) => void
}) {
  const [query, setQuery] = useState('')
  const visible = tasks.filter((task) => task.title.toLowerCase().includes(query.trim().toLowerCase()))

  return (
    <section className="rounded-2xl bg-surface-container-lowest/90 p-5 shadow-[0_0_40px_rgba(45,52,53,0.04)] dark:bg-dark-surface-container-lowest/85" aria-label="Ready to Plan tasks">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-label text-[10px] uppercase tracking-[0.16em] text-primary">Battle Plan</p>
          <h2 className="mt-1 font-headline text-xl font-light text-on-surface">Ready to Plan</h2>
          <p className="mt-1 text-xs leading-relaxed text-on-surface-variant">Drag a task to Planned, or select it and choose a time slot.</p>
        </div>
        <span className="rounded-full bg-surface-container px-2 py-1 text-xs text-on-surface-variant">{tasks.length}</span>
      </div>

      {tasks.length > 4 ? (
        <input
          type="search"
          aria-label="Search Ready to Plan tasks"
          placeholder="Search tasks"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="mt-4 w-full rounded-xl border border-outline-variant/25 bg-surface px-3 py-2 text-sm outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/20 dark:border-dark-outline-variant dark:bg-dark-surface"
        />
      ) : null}

      <div className="mt-4 space-y-2">
        {visible.map((task) => {
          return (
            <ReadyToPlanTaskCard
              key={task.id}
              task={task}
              selected={task.id === selectedTaskId}
              dragInstance={dragInstance}
              disabled={busyTaskId != null}
              onSelect={() => onSelect(task.id === selectedTaskId ? null : task.id)}
            />
          )
        })}
        {visible.length === 0 ? (
          <p className="rounded-xl border border-dashed border-outline-variant/30 px-3 py-5 text-center text-xs text-on-surface-variant">
            {tasks.length === 0 ? 'No tasks are waiting to be planned.' : 'No matching tasks.'}
          </p>
        ) : null}
      </div>
    </section>
  )
}

function ReadyToPlanTaskCard({ task, selected, dragInstance, disabled, onSelect }: {
  task: BattleTask
  selected: boolean
  dragInstance: 'mobile' | 'desktop'
  disabled: boolean
  onSelect: () => void
}) {
  const displayTitle = task.recurrence_kind === 'quota_session' && task.parent_title
    ? `${task.parent_title} · ${task.title}`
    : task.title
  const { ref, handleRef, isDragging } = useDraggable({
    id: `ready-task:${dragInstance}:${task.id}`,
    type: READY_TASK_DRAG_TYPE,
    data: { taskId: task.id },
    sensors: [PointerSensor],
    disabled,
  })

  return (
    <div
      ref={ref}
      data-ready-task-id={task.id}
      data-dragging={isDragging ? 'true' : undefined}
      className={`flex w-full items-stretch rounded-xl border text-left transition ${selected ? 'border-primary/40 bg-primary/10 ring-1 ring-primary/15' : 'border-outline-variant/20 bg-surface hover:border-primary/25 dark:border-dark-outline-variant dark:bg-dark-surface'} ${isDragging ? 'z-80 cursor-grabbing opacity-80 shadow-xl' : ''}`}
    >
      <button
        type="button"
        aria-pressed={selected}
        disabled={disabled}
        onClick={onSelect}
        className="min-w-0 flex-1 px-3 py-3 text-left disabled:opacity-55"
      >
        <span className="block truncate text-sm font-medium text-on-surface">{displayTitle}</span>
        <span className="mt-1 block text-xs text-on-surface-variant">
          {task.task_type?.name ?? 'Unspecified'}
        </span>
      </button>
      <button
        ref={handleRef}
        type="button"
        disabled={disabled}
        aria-label={`Drag ${displayTitle} to Planned timeline`}
        title="Drag onto the Planned timeline"
        className="flex w-11 shrink-0 touch-none cursor-grab items-center justify-center rounded-r-xl text-on-surface-variant/65 hover:bg-primary/8 hover:text-primary active:cursor-grabbing disabled:cursor-wait disabled:opacity-40"
      >
        <span className="material-symbols-outlined text-[20px]" aria-hidden>drag_indicator</span>
      </button>
    </div>
  )
}
