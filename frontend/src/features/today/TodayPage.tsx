import { DragDropProvider, PointerSensor, useDraggable, type DragEndEvent } from '@dnd-kit/react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { DayCalendarPopover } from '../../components/DayCalendarPopover'
import {
  DayTimeline,
  PLANNED_LANE_DROP_ID,
  READY_TASK_DRAG_TYPE,
} from '../../components/DayTimeline'
import { Layout } from '../../components/Layout'
import { TimeBlockInspectorContent } from '../../components/TimeBlockInspectorContent'
import { TimeBlockModal } from '../../components/TimeBlockModal'
import { api, type ActualBlock, type BattleTask, type BlockDraftPlacement, type BlockLane, type DayRead, type TaskType, type TimeBlock } from '../../lib/api'
import { WorkMode } from './WorkMode'
import { beginStoredWorkMode, readStoredWorkMode, writeStoredWorkMode, type StoredWorkMode } from './workModeState'
import { dateInTimeZone } from '../../lib/battlePlan'
import {
  addDaysIso,
  minuteFromPointerYInVisibleLane,
  SLOT_MINUTES,
  TIMELINE_SLOT_HEIGHT_PX,
  visibleMinuteRange,
  zonedLocalDateTimeToIso,
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

function minuteInTimeZone(instant: string, timezone: string): number {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(instant))
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return Number(values.hour) * 60 + Number(values.minute)
}

function blockInstant(date: string, minute: number, timezone: string) {
  const hour = String(Math.floor(minute / 60)).padStart(2, '0')
  const mins = String(minute % 60).padStart(2, '0')
  return zonedLocalDateTimeToIso(`${date}T${hour}:${mins}`, timezone)
}

export function TodayPage() {
  const { date } = useParams<{ date: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [day, setDay] = useState<DayRead | null>(null)
  const [taskTypes, setTaskTypes] = useState<TaskType[]>([])
  const [battleTasks, setBattleTasks] = useState<BattleTask[]>([])
  const [planningTaskId, setPlanningTaskId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [completionUndo, setCompletionUndo] = useState<{ taskId: number; token: string; removed: number } | null>(null)
  const [dayNotice, setDayNotice] = useState<string | null>(null)
  const [recordActualUndo, setRecordActualUndo] = useState<{ plannedBlockId: number; token: string } | null>(null)
  const [workMode, setWorkMode] = useState<StoredWorkMode | null>(() => readStoredWorkMode())
  const [workModeVisible, setWorkModeVisible] = useState(() => readStoredWorkMode() != null)
  const [workModeActual, setWorkModeActual] = useState<ActualBlock | null>(null)
  const [workModeBusy, setWorkModeBusy] = useState(false)
  const [workModeError, setWorkModeError] = useState<string | null>(null)
  const [workModeGuard, setWorkModeGuard] = useState(false)
  const [restoreWorkMode, setRestoreWorkMode] = useState(false)
  const [planThenWork, setPlanThenWork] = useState(false)
  const [nowIso, setNowIso] = useState<string | null>(null)
  const allBattleTasks = useMemo(
    () => battleTasks.flatMap((task) => [task, ...(task.session_tasks ?? [])]),
    [battleTasks],
  )
  const [selectedBlockRef, setSelectedBlockRef] = useState<{ id: number; lane: BlockLane } | null>(null)
  const selectedBlockId = selectedBlockRef?.id ?? null
  const [draft, setDraft] = useState<BlockDraftPlacement | null>(null)
  const [inspectorDirty, setInspectorDirty] = useState(false)
  const [blockDragActive, setBlockDragActive] = useState(false)
  const timelineRef = useRef<HTMLDivElement>(null)
  const draftCommitInFlightRef = useRef(false)
  const planningTaskInFlightRef = useRef(false)
  const clockAnchorRef = useRef<{ server: number; client: number } | null>(null)
  const workModeRequestRef = useRef<string | null>(null)
  const workTransitionRef = useRef(false)
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

  const serverNowIso = day?.meta.server_now_iso
  useEffect(() => {
    if (!serverNowIso) return
    const server = new Date(serverNowIso).getTime()
    if (!Number.isFinite(server)) return
    const anchor = { server, client: Date.now() }
    clockAnchorRef.current = anchor
    const update = () => setNowIso(new Date(anchor.server + Date.now() - anchor.client).toISOString())
    update()
    const interval = window.setInterval(update, 1000)
    return () => window.clearInterval(interval)
  }, [serverNowIso])

  const presentInstant = useCallback(() => {
    const anchor = clockAnchorRef.current
    return anchor
      ? new Date(anchor.server + Date.now() - anchor.client).toISOString()
      : new Date().toISOString()
  }, [])

  const persistWorkMode = useCallback((next: StoredWorkMode | null) => {
    setWorkMode(next)
    writeStoredWorkMode(next)
  }, [])

  const enterWorkMode = useCallback((entryAt = presentInstant()) => {
    const next = beginStoredWorkMode(entryAt)
    setWorkMode(next)
    setWorkModeGuard(false)
    setRestoreWorkMode(false)
    setWorkModeVisible(true)
    setSelectedBlockRef(null)
    setDraft(null)
    setWorkModeError(null)
    return next
  }, [presentInstant])

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

  const nowMinute = day && nowIso ? minuteInTimeZone(nowIso, day.meta.timezone) : 0
  const todayPlannedBlocks = useMemo(
    () => day && nowIso && dateInTimeZone(nowIso, day.meta.timezone) === day.date
      ? day.time_blocks.filter((block) => block.lane === 'planned').sort((a, b) => a.start_minute - b.start_minute)
      : [],
    [day, nowIso],
  )
  const currentWorkBlock = todayPlannedBlocks.find(
    (block) => block.start_minute <= nowMinute && nowMinute < block.end_minute,
  ) ?? null
  const nextWorkBlock = todayPlannedBlocks.find((block) => block.start_minute > nowMinute) ?? null

  const workModeCurrentBlock = useMemo<TimeBlock | null>(() => {
    if (!workModeActual || workModeActual.end_at != null) return currentWorkBlock
    const linked = workModeActual.planned_block_id == null
      ? null
      : day?.time_blocks.find((block) => block.id === workModeActual.planned_block_id && block.lane === 'planned') ?? null
    if (linked) return linked
    const startMinute = day ? minuteInTimeZone(workModeActual.start_at, day.meta.timezone) : nowMinute
    return {
      ...workModeActual,
      lane: 'actual',
      start_minute: startMinute,
      end_minute: Math.max(startMinute + 1, nowMinute),
    }
  }, [currentWorkBlock, day, nowMinute, workModeActual])

  const workModeNextBlock = workModeActual
    ? (nextWorkBlock?.id === workModeActual.planned_block_id ? null : nextWorkBlock)
    : nextWorkBlock

  const workModeTask = workModeCurrentBlock?.task_id == null
    ? null
    : allBattleTasks.find((task) => task.id === workModeCurrentBlock.task_id) ?? null

  useEffect(() => {
    if (!selectedBlockRef || !day) return
    const exists = selectedBlockRef.lane === 'actual'
      ? day.actual_blocks.some(({ actual_block }) => actual_block.id === selectedBlockRef.id)
      : day.time_blocks.some((block) => block.id === selectedBlockRef.id && block.lane === 'planned')
    if (!exists) setSelectedBlockRef(null)
  }, [day, selectedBlockRef])

  useEffect(() => {
    if (!day || !nowIso) return
    const stored = readStoredWorkMode()
    if (stored && new Date(nowIso).getTime() - new Date(stored.lastObservedAt).getTime() > 10 * 60_000) {
      setWorkMode(stored)
      setRestoreWorkMode(true)
    }
  }, [day, nowIso])

  useEffect(() => {
    if (!workMode || workMode.activeActualId == null || workModeActual?.id === workMode.activeActualId) return
    let cancelled = false
    void api.getActiveActualBlock().then((active) => {
      if (!cancelled && active?.id === workMode.activeActualId) setWorkModeActual(active)
    }).catch(() => undefined)
    return () => { cancelled = true }
  }, [workMode, workModeActual])

  useEffect(() => {
    if (!day || !nowIso || !workMode) return
    const currentDate = dateInTimeZone(nowIso, day.meta.timezone)
    if (currentDate !== day.date) navigate(`/day/${currentDate}?workMode=start`, { replace: true })
  }, [day, navigate, nowIso, workMode])

  useEffect(() => {
    if (searchParams.get('workMode') !== 'start') {
      workModeRequestRef.current = null
      return
    }
    if (!day || !nowIso || restoreWorkMode) return
    if (workMode && new Date(nowIso).getTime() - new Date(workMode.lastObservedAt).getTime() > 10 * 60_000) return
    const requestKey = `${day.date}|${searchParams.toString()}`
    if (workModeRequestRef.current === requestKey) return
    workModeRequestRef.current = requestKey
    void (async () => {
      const active = await api.getActiveActualBlock().catch(() => null)
      if (active) {
        setWorkModeVisible(true)
        setWorkModeActual(active)
        const existing = readStoredWorkMode()
        const next = existing ?? beginStoredWorkMode(active.start_at)
        const restored = {
          ...next,
          activeActualId: active.id,
          activePlannedBlockId: active.planned_block_id,
          activePlannedEndAt: (() => {
            if (active.planned_block_id == null) return next.activePlannedEndAt
            const block = day.time_blocks.find((candidate) => candidate.id === active.planned_block_id && candidate.lane === 'planned')
            return block ? blockInstant(day.date, block.end_minute, day.meta.timezone) : next.activePlannedEndAt
          })(),
          lastObservedAt: nowIso,
          lastConfirmedAt: nowIso,
        }
        persistWorkMode(restored)
      } else if (!workMode) {
        const near = currentWorkBlock != null || (nextWorkBlock != null && nextWorkBlock.start_minute - nowMinute <= 10)
        if (near) enterWorkMode(nowIso)
        else setWorkModeGuard(true)
      } else setWorkModeVisible(true)
      navigate(`/day/${day.meta.today}`, { replace: true })
    })()
  }, [currentWorkBlock, day, enterWorkMode, navigate, nextWorkBlock, nowIso, nowMinute, persistWorkMode, restoreWorkMode, searchParams, workMode])

  useEffect(() => {
    if (!day) return
    const requestedId = Number(searchParams.get('block'))
    if (!Number.isInteger(requestedId) || !day.time_blocks.some((block) => block.id === requestedId)) return
    setDraft(null)
    const requested = day.time_blocks.find((block) => block.id === requestedId)
    if (requested) setSelectedBlockRef({ id: requested.id, lane: requested.lane })
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
    setPlanThenWork(false)
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
      const task = allBattleTasks.find((item) => item.id === taskId && item.ready_to_plan)
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
        const refreshed = await api.listBattleTasks('active').catch(() => null)
        if (refreshed) setBattleTasks(refreshed.items)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to plan task')
      } finally {
        planningTaskInFlightRef.current = false
        setPlanningTaskBusyId(null)
      }
    },
    [allBattleTasks, date, day, resolvePlanningTaskType],
  )

  const onLaneSlotClick = useCallback(
    (lane: BlockLane, startMin: number, endMin: number) => {
      if (!tryDiscardIfNeeded()) return
      const planningTask = allBattleTasks.find((task) => task.id === planningTaskId)
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
      setSelectedBlockRef(null)
    },
    [allBattleTasks, planReadyTaskAt, planningTaskId, tryDiscardIfNeeded],
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
        if (draft.lane === 'actual') {
          if (!day) return
          const local = (minute: number) => `${date}T${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
          await api.createActualBlock({
            task_type_id: payload.task_type_id,
            task_id: draft.task_id ?? null,
            note: payload.note,
            start_at: zonedLocalDateTimeToIso(local(draft.start_minute), day.meta.timezone),
            end_at: zonedLocalDateTimeToIso(local(draft.end_minute), day.meta.timezone),
          })
          setDay(await api.getDay(date))
          setDraft(null)
          return
        }
        const next = await api.createBlock(date, {
          lane: 'planned', task_type_id: payload.task_type_id, task_id: draft.task_id ?? null,
          note: payload.note ?? undefined, start_minute: draft.start_minute, end_minute: draft.end_minute,
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
        if (created) setSelectedBlockRef({ id: created.id, lane: created.lane })
        if (created && planThenWork) {
          const completedAt = presentInstant()
          const completedMinute = minuteInTimeZone(completedAt, next.meta.timezone)
          if (
            (created.start_minute <= completedMinute && completedMinute < created.end_minute) ||
            (created.start_minute > completedMinute && created.start_minute - completedMinute <= 10)
          ) {
            enterWorkMode(completedAt)
          }
          setPlanThenWork(false)
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to create block')
        throw e
      } finally {
        draftCommitInFlightRef.current = false
      }
    },
    [date, day, draft, enterWorkMode, planThenWork, presentInstant],
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

  const patchActual = useCallback(
    async (blockId: number, patch: { task_type_id?: number; note?: string | null }) => {
      if (!date) return
      setError(null)
      try {
        await api.patchActualBlock(blockId, patch)
        setDay(await api.getDay(date))
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'Failed to update Actual block')
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
        await api.deleteActualBlock(blockId)
        setDay(await api.getDay(date))
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'Failed to delete Actual block')
        throw cause
      }
    },
    [date],
  )

  const recordActualAsPlanned = useCallback(
    async (blockId: number) => {
      if (!date) return
      setError(null)
      try {
        const result = await api.recordActualAsPlanned(blockId)
        setRecordActualUndo({ plannedBlockId: blockId, token: result.undo_token })
        setDay(await api.getDay(date))
      } catch (e) {
        const msg = e instanceof Error ? e.message : 'Failed to record Actual as planned'
        setError(msg)
        throw e
      }
    },
    [date],
  )

  useEffect(() => {
    if (!workMode || !day || !nowIso || restoreWorkMode || workTransitionRef.current) return
    if (new Date(nowIso).getTime() - new Date(workMode.lastObservedAt).getTime() > 10 * 60_000) return
    const run = async () => {
      workTransitionRef.current = true
      try {
        let nextState = workMode
        const nowMs = new Date(nowIso).getTime()

        if (nextState.activeActualId != null && nextState.activePlannedEndAt) {
          const boundaryMs = new Date(nextState.activePlannedEndAt).getTime()
          if (nowMs >= boundaryMs) {
            await api.patchActualBlock(nextState.activeActualId, { end_at: nextState.activePlannedEndAt })
            setWorkModeActual(null)
            nextState = {
              ...nextState,
              activeActualId: null,
              activePlannedBlockId: null,
              activePlannedEndAt: null,
              confirmingPlannedBlockId: null,
              confirmationStartedAt: null,
              lastConfirmedAt: nextState.activePlannedEndAt,
            }
          }
        }

        if (
          nextState.activeActualId == null &&
          nextState.confirmingPlannedBlockId != null &&
          nextState.confirmationStartedAt != null
        ) {
          const confirmed = day.time_blocks.find(
            (block) => block.id === nextState.confirmingPlannedBlockId && block.lane === 'planned',
          )
          if (confirmed) {
            const blockStartAt = blockInstant(day.date, confirmed.start_minute, day.meta.timezone)
            const blockEndAt = blockInstant(day.date, confirmed.end_minute, day.meta.timezone)
            const actualStartAt = new Date(blockStartAt) > new Date(nextState.entryAt) ? blockStartAt : nextState.entryAt
            const blockEndMs = new Date(blockEndAt).getTime()
            const confirmedDurationMs = Math.min(nowMs, blockEndMs) - new Date(nextState.confirmationStartedAt).getTime()
            if (confirmedDurationMs < 60_000 && nowMs >= blockEndMs) {
              nextState = { ...nextState, confirmingPlannedBlockId: null, confirmationStartedAt: null }
            } else if (confirmedDurationMs >= 60_000 && nowMs >= blockEndMs) {
              await api.createActualBlock({
                planned_block_id: confirmed.id,
                start_at: actualStartAt,
                end_at: blockEndAt,
              })
              nextState = {
                ...nextState,
                confirmingPlannedBlockId: null,
                confirmationStartedAt: null,
                lastConfirmedAt: blockEndAt,
              }
            } else if (confirmedDurationMs >= 60_000) {
              const actual = await api.startActualBlock({ planned_block_id: confirmed.id, start_at: actualStartAt })
              setWorkModeActual(actual)
              nextState = {
                ...nextState,
                activeActualId: actual.id,
                activePlannedBlockId: confirmed.id,
                activePlannedEndAt: blockEndAt,
                confirmingPlannedBlockId: null,
                confirmationStartedAt: null,
                lastConfirmedAt: nowIso,
              }
            }
          } else {
            nextState = { ...nextState, confirmingPlannedBlockId: null, confirmationStartedAt: null }
          }
        }

        if (nextState.activeActualId != null) {
          if (nextState.lastConfirmedAt !== nowIso) nextState = { ...nextState, lastConfirmedAt: nowIso }
        } else if (!currentWorkBlock) {
          if (nextState.confirmingPlannedBlockId != null) {
            nextState = { ...nextState, confirmingPlannedBlockId: null, confirmationStartedAt: null }
          }
        } else if (nextState.activePlannedBlockId !== currentWorkBlock.id) {
          const blockStartAt = blockInstant(day.date, currentWorkBlock.start_minute, day.meta.timezone)
          if (nextState.confirmingPlannedBlockId !== currentWorkBlock.id || !nextState.confirmationStartedAt) {
            const confirmationStartedAt = new Date(blockStartAt) > new Date(nextState.entryAt)
              ? blockStartAt
              : nextState.entryAt
            nextState = {
              ...nextState,
              confirmingPlannedBlockId: currentWorkBlock.id,
              confirmationStartedAt,
            }
          } else if (nowMs - new Date(nextState.confirmationStartedAt).getTime() >= 60_000) {
            const actualStartAt = new Date(blockStartAt) > new Date(nextState.entryAt) ? blockStartAt : nextState.entryAt
            const actual = await api.startActualBlock({ planned_block_id: currentWorkBlock.id, start_at: actualStartAt })
            setWorkModeActual(actual)
            nextState = {
              ...nextState,
              activeActualId: actual.id,
              activePlannedBlockId: currentWorkBlock.id,
              activePlannedEndAt: blockInstant(day.date, currentWorkBlock.end_minute, day.meta.timezone),
              confirmingPlannedBlockId: null,
              confirmationStartedAt: null,
              lastConfirmedAt: nowIso,
            }
          }
        }

        if (nowMs - new Date(nextState.lastObservedAt).getTime() >= 30_000) {
          nextState = { ...nextState, lastObservedAt: nowIso }
        }
        if (nextState !== workMode) persistWorkMode(nextState)
      } catch (cause) {
        setWorkModeError(cause instanceof Error ? cause.message : 'Work Mode could not update Actual time')
        const active = await api.getActiveActualBlock().catch(() => null)
        if (active) {
          setWorkModeActual(active)
          persistWorkMode({
            ...workMode,
            activeActualId: active.id,
            activePlannedBlockId: active.planned_block_id,
            lastObservedAt: nowIso,
          })
        }
      } finally {
        workTransitionRef.current = false
      }
    }
    void run()
  }, [currentWorkBlock, day, nowIso, persistWorkMode, restoreWorkMode, workMode])

  const exitWorkMode = useCallback(async () => {
    if (!workMode) return
    setWorkModeBusy(true)
    setWorkModeError(null)
    try {
      const activeId = workModeActual?.id ?? workMode.activeActualId
      if (activeId != null) await api.patchActualBlock(activeId, { end_at: presentInstant() })
      setWorkModeActual(null)
      persistWorkMode(null)
      setDayNotice('Actual time preserved · Task remains open.')
      await load()
    } catch (cause) {
      setWorkModeError(cause instanceof Error ? cause.message : 'Failed to exit Work Mode')
    } finally {
      setWorkModeBusy(false)
    }
  }, [load, persistWorkMode, presentInstant, workMode, workModeActual])

  const continueAfterAbsence = useCallback(async () => {
    if (!workMode || !day) return
    setWorkModeBusy(true)
    setWorkModeError(null)
    const resumedAt = presentInstant()
    try {
      let nextState = workMode
      let endedActivePlannedBlockId: number | null = null
      const resumedMs = new Date(resumedAt).getTime()
      const cutoffMs = new Date(workMode.lastConfirmedAt).getTime()

      if (nextState.activeActualId != null && nextState.activePlannedEndAt && new Date(nextState.activePlannedEndAt).getTime() <= resumedMs) {
        endedActivePlannedBlockId = nextState.activePlannedBlockId
        await api.patchActualBlock(nextState.activeActualId, { end_at: nextState.activePlannedEndAt })
        nextState = { ...nextState, activeActualId: null, activePlannedBlockId: null, activePlannedEndAt: null }
        setWorkModeActual(null)
      }

      for (const block of todayPlannedBlocks) {
        if (block.id === nextState.activePlannedBlockId || block.id === endedActivePlannedBlockId) continue
        const startAt = blockInstant(day.date, block.start_minute, day.meta.timezone)
        const endAt = blockInstant(day.date, block.end_minute, day.meta.timezone)
        const startMs = Math.max(new Date(startAt).getTime(), cutoffMs, new Date(workMode.entryAt).getTime())
        const endMs = Math.min(new Date(endAt).getTime(), resumedMs)
        if (endMs <= startMs) continue
        if (new Date(endAt).getTime() <= resumedMs) {
          await api.createActualBlock({
            planned_block_id: block.id,
            start_at: new Date(startMs).toISOString(),
            end_at: new Date(endMs).toISOString(),
          })
        } else {
          const actual = await api.startActualBlock({
            planned_block_id: block.id,
            start_at: new Date(startMs).toISOString(),
          })
          setWorkModeActual(actual)
          nextState = {
            ...nextState,
            activeActualId: actual.id,
            activePlannedBlockId: block.id,
            activePlannedEndAt: endAt,
          }
        }
      }
      persistWorkMode({
        ...nextState,
        lastObservedAt: resumedAt,
        lastConfirmedAt: resumedAt,
        confirmingPlannedBlockId: null,
        confirmationStartedAt: null,
      })
      setRestoreWorkMode(false)
      setDay(await api.getDay(day.date))
    } catch (cause) {
      setWorkModeError(cause instanceof Error ? cause.message : 'Failed to backfill Work Mode')
    } finally {
      setWorkModeBusy(false)
    }
  }, [day, persistWorkMode, presentInstant, todayPlannedBlocks, workMode])

  const declineAfterAbsence = useCallback(async () => {
    if (!workMode) return
    setWorkModeBusy(true)
    try {
      const activeId = workModeActual?.id ?? workMode.activeActualId
      if (activeId != null) await api.patchActualBlock(activeId, { end_at: workMode.lastConfirmedAt })
      setWorkModeActual(null)
      persistWorkMode(null)
      setRestoreWorkMode(false)
    } catch (cause) {
      setWorkModeError(cause instanceof Error ? cause.message : 'Failed to restore Work Mode')
    } finally {
      setWorkModeBusy(false)
    }
  }, [persistWorkMode, workMode, workModeActual])

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
    (blockId: number, lane: BlockLane): boolean => {
      if (!tryDiscardIfNeeded()) return false
      setDraft(null)
      if (lane === 'actual' && day) {
        const actual = day.actual_blocks.find((projection) => projection.actual_block.id === blockId)?.actual_block
        if (actual && actual.end_at == null) {
          const resumed = readStoredWorkMode() ?? beginStoredWorkMode(actual.start_at)
          persistWorkMode({
            ...resumed,
            activeActualId: actual.id,
            activePlannedBlockId: actual.planned_block_id,
            activePlannedEndAt: (() => {
              if (actual.planned_block_id == null) return resumed.activePlannedEndAt
              const planned = day.time_blocks.find((candidate) => candidate.id === actual.planned_block_id && candidate.lane === 'planned')
              return planned ? blockInstant(day.date, planned.end_minute, day.meta.timezone) : resumed.activePlannedEndAt
            })(),
            lastObservedAt: presentInstant(),
            lastConfirmedAt: presentInstant(),
          })
          setWorkModeActual(actual)
          setSelectedBlockRef(null)
          return true
        }
        setSelectedBlockRef(actual ? { id: blockId, lane: 'actual' } : null)
        return Boolean(actual)
      }
      setSelectedBlockRef({ id: blockId, lane })
      return true
    },
    [day, persistWorkMode, presentInstant, tryDiscardIfNeeded],
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

  const mobileSheetOpen = selectedBlock != null || draft != null
  const readyTasks = allBattleTasks.filter((task) => task.ready_to_plan)
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

          {completionUndo ? (
            <div className="mb-6 flex items-center justify-between gap-3 rounded-xl bg-surface-container-low px-4 py-3 text-sm text-on-surface">
              <span>Task completed · {completionUndo.removed} future Planned {completionUndo.removed === 1 ? 'Block' : 'Blocks'} removed.</span>
              <button
                type="button"
                className="font-medium text-primary underline"
                onClick={async () => {
                  setError(null)
                  try {
                    await api.undoBattleTaskCompletion(completionUndo.taskId, completionUndo.token)
                    setCompletionUndo(null)
                    await load()
                  } catch (cause) { setError(cause instanceof Error ? cause.message : 'Failed to undo Task Completion') }
                }}
              >
                Undo
              </button>
            </div>
          ) : null}
          {dayNotice ? <div role="status" className="mb-6 rounded-xl bg-surface-container-low px-4 py-3 text-sm text-on-surface">{dayNotice}</div> : null}
          {recordActualUndo ? (
            <div className="mb-6 flex items-center justify-between gap-3 rounded-xl bg-surface-container-low px-4 py-3 text-sm text-on-surface">
              <span>Actual recorded as planned.</span>
              <button type="button" className="font-medium text-primary underline" onClick={async () => {
                setError(null)
                try {
                  await api.undoRecordActualAsPlanned(recordActualUndo.plannedBlockId, recordActualUndo.token)
                  setRecordActualUndo(null)
                  setDay(await api.getDay(date))
                } catch (cause) { setError(cause instanceof Error ? cause.message : 'Failed to undo recorded Actual') }
              }}>Undo</button>
            </div>
          ) : null}

          <div className="mb-6 lg:hidden">
            <ReadyToPlanDrawer
              tasks={readyTasks}
              selectedTaskId={planningTaskId}
              dragInstance="mobile"
              busyTaskId={planningTaskBusyId}
              onSelect={(taskId) => {
                setPlanningTaskId(taskId)
                setDraft(null)
                setSelectedBlockRef(null)
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
              onBlockClick={onBlockClick}
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
                  setSelectedBlockRef(null)
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
            onRecordActualAsPlanned={inspectorSharedProps.onRecordActualAsPlanned}
            onCreateTaskTypePath={createTaskTypePath}
            onDirtyChange={setInspectorDirty}
            blockDragActive={blockDragActive}
          />
        </div>
      </div>
      </DragDropProvider>
      {workMode && workModeVisible && !restoreWorkMode ? (
        <WorkMode
          current={workModeCurrentBlock}
          next={workModeNextBlock}
          task={workModeTask}
          nowMinute={nowMinute}
          confirming={workMode.confirmingPlannedBlockId != null}
          recording={workMode.activeActualId != null}
          busy={workModeBusy}
          error={workModeError}
          onSetSubtask={async (id, checked) => {
            setWorkModeBusy(true); setWorkModeError(null)
            try {
              if (checked) await api.checkSubtask(id); else await api.uncheckSubtask(id)
              setBattleTasks((await api.listBattleTasks('active')).items)
            } catch (cause) { setWorkModeError(cause instanceof Error ? cause.message : 'Failed to update Subtask') }
            finally { setWorkModeBusy(false) }
          }}
          onLeave={() => setWorkModeVisible(false)}
          onExit={exitWorkMode}
        />
      ) : null}
      {workModeGuard ? (
        <section role="dialog" aria-modal="true" aria-label="Start Work Mode" className="fixed inset-0 z-[125] grid place-items-center bg-black/35 p-5">
          <div className="w-full max-w-md rounded-3xl bg-surface p-7 shadow-xl dark:bg-dark-surface-container">
            <h2 className="font-headline text-2xl font-light">No immediate planned work</h2>
            <p className="mt-3 text-sm leading-relaxed text-on-surface-variant">There is no planned work for the immediate future. You can plan something at the current time or continue anyway.</p>
            <div className="mt-6 grid gap-2 sm:grid-cols-2">
              <button type="button" className="rounded-xl border border-outline-variant/40 px-4 py-3 text-sm" onClick={() => {
                const range = visibleMinuteRange(day)
                const start = Math.max(range.start, Math.min(Math.floor(nowMinute / SLOT_MINUTES) * SLOT_MINUTES, range.end - SLOT_MINUTES))
                setPlanThenWork(true)
                setDraft({ lane: 'planned', start_minute: start, end_minute: start + SLOT_MINUTES, task_id: null, task_type_id: null })
                setSelectedBlockRef(null)
                setWorkModeGuard(false)
              }}>Plan something first</button>
              <button type="button" className="rounded-xl bg-primary px-4 py-3 text-sm font-medium text-on-primary" onClick={() => enterWorkMode(presentInstant())}>Continue</button>
            </div>
          </div>
        </section>
      ) : null}
      {restoreWorkMode && workMode ? (
        <section role="dialog" aria-modal="true" aria-label="Restore Work Mode" className="fixed inset-0 z-[126] grid place-items-center bg-black/35 p-5">
          <div className="w-full max-w-md rounded-3xl bg-surface p-7 shadow-xl dark:bg-dark-surface-container">
            <h2 className="font-headline text-2xl font-light">Were you still working?</h2>
            <p className="mt-3 text-sm leading-relaxed text-on-surface-variant">The application was away for more than ten minutes. Confirm before Work Mode records that interval.</p>
            <div className="mt-6 grid gap-2 sm:grid-cols-2">
              <button type="button" disabled={workModeBusy} className="rounded-xl border border-outline-variant/40 px-4 py-3 text-sm" onClick={() => void declineAfterAbsence()}>No, stop at last confirmed time</button>
              <button type="button" disabled={workModeBusy} className="rounded-xl bg-primary px-4 py-3 text-sm font-medium text-on-primary" onClick={() => void continueAfterAbsence()}>Yes, I continued</button>
            </div>
          </div>
        </section>
      ) : null}
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
