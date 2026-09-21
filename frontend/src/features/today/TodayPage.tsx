import { DayViewOptions } from './DayViewOptions'
import { useDayViewPreferences } from './dayViewPreferences'
import { getFocusController } from '../activity/focusController'
import { RecordingPreview } from './RecordingPreview'
import type { PlannedRecordingResult } from '../../lib/api'
import { activityDay } from '../activity/activityDay'
import { ReportingDayActuals } from '../activity/ReportingDayActuals'
import { DragDropProvider, type DragMoveEvent, type DragOverEvent, type DragEndEvent } from '@dnd-kit/react'
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { DayCalendarPopover } from '../../components/DayCalendarPopover'
import {
  DayTimeline,
  PLANNED_LANE_DROP_ID,
  READY_TASK_DRAG_TYPE,
} from '../../components/DayTimeline'
import { Layout } from '../../components/Layout'
import { TimeBlockInspectorContent } from '../../components/TimeBlockInspectorContent'
import { api, type BattleTask, type BlockDraftPlacement, type BlockLane, type DayRead, type TaskType } from '../../lib/api'
import { actualPlacementEnd, nearestBlockStart, NO_NEARBY_BLOCK_SPACE, blockRangeAvailable } from '../../lib/blockPlacement'
import { needsElapsedDayView } from '../../lib/dayView'
import { ActivityTracking } from '../activity/ActivityTracking'
import { getActivityRepository, type ActivityCorrection } from '../activity/activityRepository'
import { useReadinessCoordinator } from '../readiness/readinessCoordinator'
import { TransientFeedback } from '../../components/TransientFeedback'
import {
  addDaysIso,
  formatTimeRangeGcal12,
  minuteFromPointerYInVisibleLane,
  SLOT_MINUTES,
  TIMELINE_SLOT_HEIGHT_PX,
  visibleMinuteRange,
  zonedLocalDateTimeToIso,
} from '../../lib/time'
import { errorMessage } from '../../lib/errors'
import { useInspectorDismiss } from './useInspectorDismiss'

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

/** Convert a Day-lane minute, including 24:00, into a valid local date-time input. */
function localDateTimeAtMinute(date: string, minute: number): string {
  const dayOffset = Math.floor(minute / (24 * 60))
  const minuteOfDay = minute - dayOffset * 24 * 60
  const hour = String(Math.floor(minuteOfDay / 60)).padStart(2, '0')
  const minutePart = String(minuteOfDay % 60).padStart(2, '0')
  return `${addDaysIso(date, dayOffset)}T${hour}:${minutePart}`
}

export function TodayPage() {
  const { preferences: dayView, change: changeDayView, storageError } = useDayViewPreferences()
  const trackingVisible = dayView.tracking
  const [viewOpen, setViewOpen] = useState(false)
  const [timelineZoom, setTimelineZoom] = useState(1)
  const readiness = useReadinessCoordinator()
  const { date } = useParams<{ date: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [storedDay, setDay] = useState<DayRead | null>(null)
  const activityRepository = useMemo(() => getActivityRepository(), [])
  const activityState = useSyncExternalStore(activityRepository.subscribe, activityRepository.getSnapshot)
  const day = useMemo(() => date && activityState.snapshot ? activityDay(date, activityState.snapshot, storedDay, activityRepository.now()) : storedDay, [date, activityState.snapshot, storedDay, activityRepository])
  const [taskTypes, setTaskTypes] = useState<TaskType[]>([])
  const [storedBattleTasks, setBattleTasks] = useState<BattleTask[]>([])
  const battleTasks = readiness.projectTasks(storedBattleTasks)
  const ingestBattleTasks = useCallback((items: BattleTask[]) => {
    readiness.observeTasks(items)
    setBattleTasks(items)
  }, [readiness])
  const [planningTaskId, setPlanningTaskId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [completionUndo, setCompletionUndo] = useState<{ taskId: number; token: string; removed: number } | null>(null)
  const [dayNotice, setDayNotice] = useState<string | null>(null)
  const recordingOperation = useRef(0)
  const [recordActualUndo, setRecordActualUndo] = useState<{ plannedBlockId: number; token: string } | null>(null)
  const [recordPreview, setRecordPreview] = useState<{ blockId: number; result: PlannedRecordingResult } | null>(null)
  const allBattleTasks = useMemo(
    () => battleTasks.flatMap((task) => [task, ...(task.session_tasks ?? [])]),
    [battleTasks],
  )
  const [selectedBlockRef, setSelectedBlockRef] = useState<{ id: number; lane: BlockLane } | null>(null)
  const selectedBlockId = selectedBlockRef?.id ?? null
  const [draft, setDraft] = useState<BlockDraftPlacement | null>(null)
  const [inspectorDirty, setInspectorDirty] = useState(false)
  const [inspectorIsRail, setInspectorIsRail] = useState(() => window.matchMedia?.('(min-width: 1280px)').matches ?? true)
  useEffect(() => {
    const media = window.matchMedia?.('(min-width: 1280px)')
    if (!media) return
    const onChange = (event: MediaQueryListEvent) => setInspectorIsRail(event.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])
  const [blockDragActive, setBlockDragActive] = useState(false)
  const timelineRef = useRef<HTMLDivElement>(null)
  const [scrollToNowRequest, setScrollToNowRequest] = useState(0)
  const draftCommitInFlightRef = useRef(false)
  const planningTaskInFlightRef = useRef(false)
  const [planningTaskBusyId, setPlanningTaskBusyId] = useState<number | null>(null)
  const [readyTaskDragging, setReadyTaskDragging] = useState(false)
  const [readyDropCandidate, setReadyDropCandidate] = useState<{ taskId: number; start: number } | null>(null)
  const readyDropPreview = useMemo(() => {
    if (!day || !readyDropCandidate) return null
    const range = visibleMinuteRange(day)
    const occupied = day.time_blocks.filter(b => b.lane === 'planned')
    const start = nearestBlockStart(draft?.lane === 'planned' ? [...occupied, draft] : occupied,
      readyDropCandidate.start, SLOT_MINUTES, range.start, range.end)
    return { taskId: readyDropCandidate.taskId, start }
  }, [day, draft, readyDropCandidate])
  const readyDropPreviewRef = useRef(readyDropPreview)
  useLayoutEffect(() => { readyDropPreviewRef.current = readyDropPreview }, [readyDropPreview])

  const [planningSaves, setPlanningSaves] = useState(0)
  const planningActive = allBattleTasks.some((task) => task.id === planningTaskId && task.ready_to_plan)
    || draft?.lane === 'planned'
    || (selectedBlockRef?.lane === 'planned' && inspectorDirty)
    || blockDragActive || readyTaskDragging || planningTaskBusyId != null || planningSaves > 0
  const planningTaskSchedulable = planningTaskId == null || readiness.isSchedulable(planningTaskId)
  const draftTaskId = draft?.task_id ?? null
  const draftTaskSchedulable = draftTaskId == null || readiness.isSchedulable(draftTaskId)

  useEffect(() => {
    if (!planningTaskSchedulable) setPlanningTaskId(null)
    if (!draftTaskSchedulable) {
      setDraft(null)
      setInspectorDirty(false)
    }
  }, [draftTaskSchedulable, planningTaskSchedulable])

  useLayoutEffect(() => {
    getFocusController().setPlanning(planningActive)
  }, [planningActive])

  const load = useCallback(async () => {
    if (!date) return
    setLoading(true)
    setError(null)
    try {
      const [d, tt, battle] = await Promise.all([
        api.getDay(date),
        api.listTaskTypes(),
        api.listBattleTasks('active', date).catch(() => null),
      ])
      setDay(d)
      setTaskTypes(tt)
      ingestBattleTasks(battle?.items ?? [])
    } catch (e) {
      setError(errorMessage(e, 'Failed to load day'))
    } finally {
      setLoading(false)
    }
  }, [date, ingestBattleTasks])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const changed = () => { void load(); void activityRepository.refresh() }
    window.addEventListener('timebox:focus-task-changed', changed)
    return () => window.removeEventListener('timebox:focus-task-changed', changed)
  }, [load, activityRepository])


  useEffect(() => {
    setSelectedBlockRef(null)
    setDraft(null)
    setInspectorDirty(false)
  }, [date])

  const selectedBlock = useMemo(() => {
    if (!selectedBlockRef || !day) return null
    if (selectedBlockRef.lane === 'planned') {
      return day.time_blocks.find((block) => block.id === selectedBlockRef.id && block.lane === 'planned') ?? null
    }
    const projection = day.actual_blocks.find(({ actual_block }) => actual_block.id === selectedBlockRef.id)
    if (!projection) return null
    return {
      ...projection.actual_block,
      lane: 'actual' as const,
      start_minute: projection.start_minute,
      end_minute: projection.end_minute,
    }
  }, [day, selectedBlockRef])

  useEffect(() => {
    if (!selectedBlockRef || !day) return
    const exists = selectedBlockRef.lane === 'actual'
      ? day.actual_blocks.some(({ actual_block }) => actual_block.id === selectedBlockRef.id)
      : day.time_blocks.some((block) => block.id === selectedBlockRef.id && block.lane === 'planned')
    if (!exists) setSelectedBlockRef(null)
  }, [day, selectedBlockRef])


  useEffect(() => {
    if (!day) return
    const requestedId = Number(searchParams.get('block'))
    if (!Number.isInteger(requestedId)) return
    const planned = day.time_blocks.find((block) => block.id === requestedId)
    const actual = day.actual_blocks.find((projection) => projection.actual_block.id === requestedId)
    if (!planned && !actual) return
    setDraft(null)
    if (planned) setSelectedBlockRef({ id: planned.id, lane: planned.lane })
    else if (actual) setSelectedBlockRef({ id: actual.actual_block.id, lane: 'actual' })
    requestAnimationFrame(() => {
      document.querySelector<HTMLElement>(`[data-block-id="${requestedId}"]`)?.scrollIntoView({
        block: 'center',
        behavior: 'smooth',
      })
    })
  }, [day, searchParams])

  const tryDiscardIfNeeded = useCallback(() => {
    if (!inspectorDirty) return true
    return confirmDiscardUnsaved()
  }, [inspectorDirty])

  const tryClosePanel = useCallback(() => {
    if (!tryDiscardIfNeeded()) return
    setSelectedBlockRef(null)
    setDraft(null)
    setInspectorDirty(false)
  }, [tryDiscardIfNeeded])

  const planReadyTaskAt = useCallback(
    async (taskId: number, startMinute: number, previewed = false) => {
      if (!date || !day || planningTaskInFlightRef.current) return
      const task = allBattleTasks.find((item) => item.id === taskId && item.ready_to_plan && readiness.isSchedulable(item.id))
      if (!task) return

      const { start: visibleStart, end: visibleEnd } = visibleMinuteRange(day)
      const saved = day.time_blocks.filter(block => block.lane === 'planned')
      const occupied = draft?.lane === 'planned' ? [...saved, draft] : saved
      const start = previewed ? startMinute
        : nearestBlockStart(occupied, startMinute, SLOT_MINUTES, visibleStart, visibleEnd)
      if (start === null) { setError(NO_NEARBY_BLOCK_SPACE); return }
      const end = start + SLOT_MINUTES
      if (!blockRangeAvailable(occupied, start, end, visibleStart, visibleEnd)) {
        setError('That time is no longer available')
        void api.getDay(date).then(setDay).catch(() => {})
        return
      }

      planningTaskInFlightRef.current = true
      setPlanningTaskBusyId(task.id)
      setError(null)
      try {
        const next = await api.createBlock(date, {
          lane: 'planned',
          task_id: task.id,
          start_minute: start,
          end_minute: end,
        })
        setDay(next)
        if (!previewed) {
          const created = next.time_blocks.find(b => b.lane === 'planned' && b.task_id === task.id && b.start_minute === start)
          requestAnimationFrame(() => {
            if (created) document.querySelector<HTMLElement>(`[data-block-id="${created.id}"]`)?.scrollIntoView({ block: 'nearest' })
          })
        }
        readiness.observeTasks([{ ...task, ready_to_plan: false }])
        setPlanningTaskId(null)
        setDraft(null)
        setSelectedBlockRef(null)
        setBattleTasks((items) =>
          items.map((item) => ({
            ...item,
            ready_to_plan: item.id === task.id ? false : item.ready_to_plan,
            session_tasks: item.session_tasks?.map((session) => ({
              ...session,
              ready_to_plan: session.id === task.id ? false : session.ready_to_plan,
            })),
          })),
        )
        const refreshed = await api.listBattleTasks('active', date).catch(() => null)
        if (refreshed) {
          ingestBattleTasks(refreshed.items)
        }
      } catch (e) {
        setError(errorMessage(e, 'Failed to plan task'))
        void api.getDay(date).then(setDay).catch(() => {})
      } finally {
        planningTaskInFlightRef.current = false
        setPlanningTaskBusyId(null)
      }
    },
    [allBattleTasks, date, day, draft, ingestBattleTasks, readiness],
  )

  const onLaneSlotClick = useCallback(
    (lane: BlockLane, startMin: number, endMin: number) => {
      if (!tryDiscardIfNeeded()) return
      const planningTask = allBattleTasks.find((task) => task.id === planningTaskId)
      if (lane === 'planned' && planningTask) {
        void planReadyTaskAt(planningTask.id, startMin)
        return
      }
      if (day) {
        const range = visibleMinuteRange(day)
        const occupied = lane === 'planned' ? day.time_blocks.filter(b => b.lane === 'planned') : day.actual_blocks
        const resolved = nearestBlockStart(occupied,
          startMin, SLOT_MINUTES, range.start, lane === 'actual' ? actualPlacementEnd(day) : range.end)
        if (resolved === null) { setError(NO_NEARBY_BLOCK_SPACE); return }
        startMin = resolved
        endMin = resolved + SLOT_MINUTES
      }
      setError(null)
      setDraft({
        lane,
        start_minute: startMin,
        end_minute: endMin,
        task_id: lane === 'planned' ? planningTask?.id ?? null : null,
        task_type_id: lane === 'planned' ? planningTask?.task_type_id ?? null : null,
      })
      setSelectedBlockRef(null)
      requestAnimationFrame(() => document.querySelector<HTMLElement>('[data-draft-block]')?.scrollIntoView({ block: 'nearest' }))
    },
    [day, allBattleTasks, planReadyTaskAt, planningTaskId, tryDiscardIfNeeded],
  )

  const onReadyTaskDragPosition = useCallback((event: DragMoveEvent | DragOverEvent) => {
    const { source, position } = event.operation
    if (source?.type !== READY_TASK_DRAG_TYPE || !day) return
    const lane = timelineRef.current?.querySelector<HTMLElement>('[data-day-lane="planned"]')
    if (!lane) return
    const rect = lane.getBoundingClientRect()
    const pointer = position.current
    if (pointer.x < rect.left || pointer.x > rect.right || pointer.y < rect.top || pointer.y > rect.bottom) {
      setReadyDropCandidate(null)
      return
    }
    const range = visibleMinuteRange(day)
    setReadyDropCandidate({ taskId: Number(source.data.taskId), start: minuteFromPointerYInVisibleLane(
      pointer.y - rect.top, range.start, range.end, Number(lane.getAttribute('data-slot-height')) || TIMELINE_SLOT_HEIGHT_PX,
    ) })
  }, [day])

  const onReadyTaskDragEnd = useCallback((event: DragEndEvent) => {
    setReadyTaskDragging(false)
    setReadyDropCandidate(null)
    const { source, target, position } = event.operation
    if (event.canceled || source?.type !== READY_TASK_DRAG_TYPE || target?.id !== PLANNED_LANE_DROP_ID) return
    const preview = readyDropPreviewRef.current
    const rect = target.element?.getBoundingClientRect()
    if (!rect || position.current.x < rect.left || position.current.x > rect.right
      || position.current.y < rect.top || position.current.y > rect.bottom) return
    if (!preview || preview.taskId !== Number(source.data.taskId)) return
    if (preview.start === null) { setError(NO_NEARBY_BLOCK_SPACE); return }
    void planReadyTaskAt(preview.taskId, preview.start, true)
  }, [planReadyTaskAt])

  const onDraftTimeChange = useCallback((startMin: number, endMin: number) => {
    setDraft((d) => (d ? { ...d, start_minute: startMin, end_minute: endMin } : null))
  }, [])

  const selectReadyTask = useCallback((taskId: number | null) => {
    setPlanningTaskId(taskId)
    setDraft((current) => {
      if (current?.lane !== 'planned') return null
      if (taskId == null) return current
      const task = allBattleTasks.find((item) => item.id === taskId)
      return task ? { ...current, task_id: task.id, task_type_id: task.task_type_id } : current
    })
    setSelectedBlockRef(null)
  }, [allBattleTasks])

  useInspectorDismiss(draft != null || selectedBlockId != null, timelineRef, tryClosePanel)

  const commitDraft = useCallback(
    async (payload: ActivityCorrection & { name: string | null; note: string | null }) => {
      if (!date || !draft || draftCommitInFlightRef.current) return
      draftCommitInFlightRef.current = true
      if (draft.lane === 'planned') setPlanningSaves((count) => count + 1)
      setError(null)
      try {
        if (draft.lane === 'actual') {
          if (!day) return
          const repo = getActivityRepository()
          if (!await repo.correct('add', null, { ...payload, task_id: draft.task_id ?? null, start_at: payload.start_at ?? zonedLocalDateTimeToIso(localDateTimeAtMinute(date, draft.start_minute), day.meta.timezone), end_at: payload.end_at ?? zonedLocalDateTimeToIso(localDateTimeAtMinute(date, draft.end_minute), day.meta.timezone) })) throw new Error(repo.state.error ?? 'Could not save correction')
          setDraft(null); setInspectorDirty(false); return
        }
        const next = await api.createBlock(date, {
          lane: 'planned', task_type_id: payload.task_type_id, task_id: draft.task_id ?? null,
          name: payload.name, note: payload.note ?? undefined, start_minute: draft.start_minute, end_minute: draft.end_minute,
        })
        setDay(next)
        const created = next.time_blocks.find(
          (b) =>
            b.lane === draft.lane &&
            b.start_minute === draft.start_minute &&
            b.end_minute === draft.end_minute &&
            (payload.task_type_id == null || b.task_type_id === payload.task_type_id),
        )
        if (draft.task_id) {
          const refreshed = await api.listBattleTasks('active', date)
          ingestBattleTasks(refreshed.items)
          setPlanningTaskId(null)
        }
        setDraft(null)
        setInspectorDirty(false)
        if (created) setSelectedBlockRef({ id: created.id, lane: created.lane })
      } catch (e) {
        setError(errorMessage(e, 'Failed to create block'))
        throw e
      } finally {
        draftCommitInFlightRef.current = false
        if (draft.lane === 'planned') setPlanningSaves((count) => count - 1)
      }
    },
    [date, day, draft, ingestBattleTasks],
  )

  const patchBlock = useCallback(
    async (
      blockId: number,
      patch: {
        task_type_id?: number
        task_id?: number | null
        name?: string | null
        note?: string | null
        start_minute?: number
        end_minute?: number
      },
      lane: BlockLane = 'planned',
    ) => {
      if (!date) return
      if (lane === 'planned') setPlanningSaves((count) => count + 1)
      setError(null)
      try {
        if (lane === 'actual') {
          if (!day) return
          const actualPatch: Partial<{
            task_type_id: number
            name: string | null
            note: string | null
            start_at: string
            end_at: string
          }> = {}
          if (patch.task_type_id !== undefined) actualPatch.task_type_id = patch.task_type_id
          if (patch.name !== undefined) actualPatch.name = patch.name
          if (patch.note !== undefined) actualPatch.note = patch.note
          if (patch.start_minute !== undefined) {
            actualPatch.start_at = zonedLocalDateTimeToIso(
              localDateTimeAtMinute(date, patch.start_minute),
              day.meta.timezone,
            )
          }
          if (patch.end_minute !== undefined) {
            actualPatch.end_at = zonedLocalDateTimeToIso(
              localDateTimeAtMinute(date, patch.end_minute),
              day.meta.timezone,
            )
          }
          const repo = getActivityRepository()
          if (!await repo.correct('edit', blockId, actualPatch)) throw new Error(repo.state.error ?? 'Could not save correction')
          return
        }
        const next = await api.patchBlock(date, blockId, patch)
        setDay(next)
      } catch (e) {
        const msg = errorMessage(e, 'Failed to update block')
        setError(msg)
        void api.getDay(date).then(setDay).catch(() => {})
        throw e
      } finally {
        if (lane === 'planned') setPlanningSaves((count) => count - 1)
      }
    },
    [date, day],
  )

  const deleteBlock = useCallback(
    async (blockId: number) => {
      if (!date) return
      setError(null)
      try {
        const next = await api.deleteBlock(date, blockId)
        setDay(next)
      } catch (e) {
        const msg = errorMessage(e, 'Failed to delete block')
        setError(msg)
        throw e
      }
    },
    [date],
  )

  const patchActual = useCallback(
    async (blockId: number, patch: ActivityCorrection) => {
      if (!date) return
      setError(null)
      try {
        const repo = getActivityRepository()
        if (!await repo.correct('edit', blockId, patch)) throw new Error(repo.state.error ?? 'Could not save correction')
        setInspectorDirty(false)
      } catch (cause) {
        setError(errorMessage(cause, 'Failed to update Actual block'))
        throw cause
      }
    },
    [date],
  )

  const deleteActual = useCallback(
    async (blockId: number) => {
      if (!date) return
      setError(null)
      try {
        const repo = getActivityRepository()
        if (!await repo.correct('delete', blockId)) throw new Error(repo.state.error ?? 'Could not save correction')
        setSelectedBlockRef(null); setInspectorDirty(false)
      } catch (cause) {
        setError(errorMessage(cause, 'Failed to delete Actual block'))
        throw cause
      }
    },
    [date],
  )

  const recordActualAsPlanned = useCallback(
    async (blockId: number, confirmation?: PlannedRecordingResult) => {
      if (!date) return
      const operation = ++recordingOperation.current
      setError(null)
      try {
        const repo = getActivityRepository()
        await repo.refresh()
        if (repo.state.pending || repo.state.error) throw new Error('Sync pending activity before recording this plan.')
        const result = await api.recordActualAsPlanned(blockId, confirmation ? { until: confirmation.end_at, fingerprint: confirmation.fingerprint } : undefined)
        if (operation !== recordingOperation.current) return
        if (result.status === 'confirmation_required') { setRecordPreview({ blockId, result }); return }
        setRecordPreview(null)
        if (result.undo_token) { setRecordActualUndo({ plannedBlockId: blockId, token: result.undo_token }); setDayNotice(null) }
        else { setRecordActualUndo(null); setDayNotice('Already recorded') }
        await getActivityRepository().refresh()
        setDay(await api.getDay(date))
      } catch (e) {
        const msg = errorMessage(e, 'Failed to record Actual as planned')
        if (operation === recordingOperation.current) setError(msg)
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
      const msg = errorMessage(e, 'Failed to create task type')
      setError(msg)
      throw e
    }
  }, [])

  const onBlockClick = useCallback(
    (blockId: number, lane: BlockLane): boolean => {
      if (lane === 'actual' && day?.actual_blocks.some(({ actual_block }) => actual_block.id === blockId && actual_block.end_at == null) && planningActive) return false
      if (!tryDiscardIfNeeded()) return false
      setDraft(null)
      if (lane === 'actual' && day) {
        const actual = day.actual_blocks.find((projection) => projection.actual_block.id === blockId)?.actual_block
        setSelectedBlockRef(actual ? { id: blockId, lane: 'actual' } : null)
        return Boolean(actual)
      }
      setSelectedBlockRef({ id: blockId, lane })
      return true
    },
    [day, planningActive, tryDiscardIfNeeded],
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
        <ActivityTracking controlsVisible={trackingVisible} taskTypes={taskTypes} onChanged={() => {}} />
        <p className="text-error">{error ?? 'Failed to load day.'}</p>
      </Layout>
    )
  }

  const hasBlockLanding = searchParams.has('block')
  const pickDay = (iso: string) => {
    if (iso === day.date) {
      if (iso === day.meta.today) setScrollToNowRequest((n) => n + 1)
      return
    }
    navigate(`/day/${iso}`)
  }

  const inspectorSharedProps = {
    onOpenActual: (id: number) => { setSelectedBlockRef({ id, lane: 'actual' }); setInspectorDirty(false) },
    day,
    taskTypes,
    onClose: tryClosePanel,
    onSave: (patch: ActivityCorrection) => {
      if (!selectedBlock) return Promise.resolve()
      return selectedBlock.lane === 'actual'
        ? patchActual(selectedBlock.id, patch)
        : patchBlock(selectedBlock.id, patch)
    },
    onCreateFromDraft: commitDraft,
    onDelete: () => {
      if (!selectedBlock) return Promise.resolve()
      return selectedBlock.lane === 'actual'
        ? deleteActual(selectedBlock.id)
        : deleteBlock(selectedBlock.id)
    },
    onRecordActualAsPlanned:
      selectedBlock?.lane === 'planned'
        ? () => selectedBlock ? recordActualAsPlanned(selectedBlock.id) : Promise.resolve()
        : undefined,
    onCreateTaskTypePath: createTaskTypePath,
    onDirtyChange: setInspectorDirty,
  }

  const inspectorOpen = selectedBlock != null || draft != null
  const readyTasks = allBattleTasks.filter((task) => task.ready_to_plan)
  const planningTask = readyTasks.find((task) => task.id === planningTaskId) ?? null

  return (
    <Layout mainClassName="w-full max-w-none bg-transparent px-6 py-6 lg:px-8 xl:px-10 dark:bg-dark-surface">
      <DragDropProvider onDragStart={(event) => { if (event.operation.source?.type === READY_TASK_DRAG_TYPE) setReadyTaskDragging(true) }} onDragMove={onReadyTaskDragPosition} onDragOver={onReadyTaskDragPosition} onDragEnd={onReadyTaskDragEnd}>
      <div className="flex flex-col gap-8 xl:flex-row xl:gap-0 xl:items-stretch">
        <div className="min-w-0 min-h-0 flex-1 xl:pr-4">
          <span data-testid="day-date" className="sr-only">
            {day.date}
          </span>
          <section className="mb-6">
            <div className="flex flex-wrap items-end justify-between gap-6">
              <div>
                <h1 className="mb-2 font-headline text-3xl sm:text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
                  <time dateTime={day.date} title={formatDisplayDate(day.date)} aria-label={formatDisplayDate(day.date)}>{new Date(`${day.date}T12:00:00Z`).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC' })}</time>
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
                {dayView.calendar && <DayCalendarPopover
                  value={day.date}
                  todayIso={day.meta.today}
                  onSelect={pickDay}
                />}
                <button type="button" className="min-h-11 rounded-full px-3 text-sm" onClick={() => pickDay(day.meta.today)}>Today</button>
                <button
                  type="button"
                  className="rounded-full border border-outline-variant/15 px-3 py-1.5 font-headline text-sm text-on-surface transition-colors hover:bg-surface-container-high dark:border-dark-outline-variant dark:text-dark-on-surface dark:hover:bg-dark-surface-container-high"
                  aria-label="Next day"
                  onClick={() => navigate(`/day/${addDaysIso(day.date, 1)}`)}
                >
                  Next →
                </button>
            {!trackingVisible && <button type="button" className="min-h-11 px-3 text-sm text-actual dark:text-actual-dark" onClick={() => changeDayView('tracking', true)}>{activityState.snapshot?.current ? '● Tracking' : 'Tracking'}</button>}
            <button type="button" className="min-h-11 rounded-full border border-outline-variant/40 px-4 text-sm dark:border-dark-outline-variant" onClick={() => setViewOpen(true)}>View</button>

              </div>
            </div>
          </section>

          {viewOpen && <DayViewOptions preferences={dayView} onChange={changeDayView} zoom={timelineZoom} onResetZoom={() => setTimelineZoom(1)} onClose={() => setViewOpen(false)} storageError={storageError} />}

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

          {completionUndo && !recordActualUndo ? (
            <TransientFeedback floating title="Task completed" detail={`${completionUndo.removed} future Planned ${completionUndo.removed === 1 ? 'Block' : 'Blocks'} removed.`} action={
              <button
                type="button"
                onClick={async () => {
                  setError(null)
                  try {
                    await api.undoBattleTaskCompletion(completionUndo.taskId, completionUndo.token)
                    setCompletionUndo(null)
                    await load()
                  } catch (cause) { setError(errorMessage(cause, 'Failed to undo Task Completion')) }
                }}
              >
                Undo
              </button>
            } />
          ) : null}
          {dayNotice && !completionUndo && !recordActualUndo ? <TransientFeedback floating title={dayNotice} /> : null}
          {recordActualUndo ? (
            <TransientFeedback floating title="Actual recorded." action={
              <button type="button" onClick={async () => {
                const undo = recordActualUndo
                const operation = ++recordingOperation.current
                setError(null)
                try {
                  const repo = getActivityRepository()
                  await repo.refresh()
                  if (repo.state.pending || repo.state.error) throw new Error('Sync pending activity before Undo.')
                  await api.undoRecordActualAsPlanned(undo.plannedBlockId, undo.token)
                  if (operation !== recordingOperation.current) return
                  setRecordActualUndo(null)
                  await repo.refresh()
                  setDay(await api.getDay(date))
                } catch (cause) { if (operation === recordingOperation.current) setError(errorMessage(cause, 'Failed to undo recorded Actual')) }
              }}>Undo</button>
            } />
          ) : null}

          {recordPreview && <RecordingPreview preview={recordPreview.result} timezone={day?.meta.timezone ?? 'UTC'} error={error}
            onCancel={() => setRecordPreview(null)} onConfirm={async () => {
              try { await recordActualAsPlanned(recordPreview.blockId, recordPreview.result) } catch { /* Error is shown by the page. */ }
            }} />}

          <div className="mb-6 xl:hidden">
            <ReadyToPlanDrawer
              tasks={readyTasks}
              selectedTaskId={planningTaskId}
              dragInstance="mobile"
              busyTaskId={planningTaskBusyId}
              onSelect={selectReadyTask}
            />
          </div>

          {planningTask ? (
            <p className="mb-3 rounded-xl bg-primary/8 px-4 py-2.5 text-sm text-on-surface">
              <strong>{planningTask.title}</strong> is selected. Choose a time in the <strong>Planned</strong> lane.
            </p>
          ) : null}

          {readyTaskDragging && readyDropPreview && (
            <div role="status" className="fixed bottom-4 left-1/2 z-80 -translate-x-1/2 rounded-lg bg-surface p-3 shadow-lg">
              {readyDropPreview.start === null ? NO_NEARBY_BLOCK_SPACE
                : formatTimeRangeGcal12(readyDropPreview.start, readyDropPreview.start + SLOT_MINUTES)}
            </div>
          )}
          <ActivityTracking controlsVisible={trackingVisible} taskTypes={taskTypes} onChanged={() => {
            void api.getDay(date).then(setDay).catch(() => {})
          }} />
          {needsElapsedDayView(day) && <ReportingDayActuals day={day} onSelect={id => onBlockClick(id, 'actual')} />}
          <section
            className="overflow-x-auto pb-24"
            data-auto-scroll-to-now={String(!hasBlockLanding || scrollToNowRequest > 0)}
          >
            <DayTimeline
              now={activityRepository.now}
              showZoomControls={dayView.zoom}
              zoom={timelineZoom}
              onZoomChange={setTimelineZoom}
              ref={timelineRef}
              day={needsElapsedDayView(day) ? { ...day, actual_blocks: [] } : day}
              readOnly={false}
              draft={draft}
              placementSelected={planningTaskId != null && !readyTaskDragging}
              placementPreview={readyDropPreview?.start != null ? { start: readyDropPreview.start, end: readyDropPreview.start + SLOT_MINUTES } : null}
              onPlacementError={setError}
              selectedBlockId={selectedBlockId}
              onLaneSlotClick={onLaneSlotClick}
              onDraftTimeChange={onDraftTimeChange}
              onPatchBlock={patchBlock}
              onBlockClick={onBlockClick}
              onBlockDragSessionChange={setBlockDragActive}
              autoScrollToNow={!hasBlockLanding || scrollToNowRequest > 0}
              scrollToNowRequest={scrollToNowRequest}
            />
          </section>
        </div>

        {/* One editor owns the draft across rail/sheet layout changes. */}
        <div
          className={`${inspectorOpen ? '' : 'hidden xl:block '}w-full shrink-0 xl:w-[min(28rem,100%)] xl:max-w-md xl:pl-6`}
          data-testid="day-inspector-rail"
        >
          <aside
            role={inspectorIsRail ? 'complementary' : 'dialog'}
            aria-label={inspectorIsRail ? 'Block details' : undefined}
            aria-modal={!inspectorIsRail && inspectorOpen ? true : undefined}
            aria-labelledby={inspectorIsRail ? undefined : 'block-panel-title'}
            data-inspector={inspectorIsRail ? 'rail' : 'sheet'}
            className={`w-full overflow-y-auto bg-surface-container-low xl:sticky xl:top-32 xl:mt-0 xl:max-h-[calc(100dvh-8.5rem)] dark:bg-dark-surface-container-low${blockDragActive ? ' pointer-events-none' : ''}`}
          >
            {selectedBlock == null && draft == null ? (
              <ReadyToPlanDrawer
                tasks={readyTasks}
                selectedTaskId={planningTaskId}
                dragInstance="desktop"
                busyTaskId={planningTaskBusyId}
                onSelect={selectReadyTask}
              />
            ) : (
              <div className="transition-opacity duration-150">
                <TimeBlockInspectorContent
                  key={
                    selectedBlock != null
                      ? `block-${selectedBlock.lane}-${selectedBlock.id}`
                      : draft != null
                        ? `draft-${draft.lane}-${draft.start_minute}-${draft.end_minute}`
                        : 'none'
                  }
                  variant={inspectorIsRail ? 'rail' : 'sheet'}
                  block={selectedBlock}
                  draft={draft}
                  {...inspectorSharedProps}
                />
              </div>
            )}
          </aside>
        </div>

      </div>
      </DragDropProvider>
    </Layout>
  )
}

import { ReadyToPlanDrawer } from './ReadyToPlanDrawer'
