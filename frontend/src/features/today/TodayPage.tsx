import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { DayCalendarPopover } from '../../components/DayCalendarPopover'
import { DayTimeline } from '../../components/DayTimeline'
import { Layout } from '../../components/Layout'
import { TimeBlockInspectorContent } from '../../components/TimeBlockInspectorContent'
import { TimeBlockModal } from '../../components/TimeBlockModal'
import { api, type BlockDraftPlacement, type BlockLane, type DayRead, type TaskType } from '../../lib/api'
import { addDaysIso } from '../../lib/time'

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
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [selectedBlockId, setSelectedBlockId] = useState<number | null>(null)
  const [draft, setDraft] = useState<BlockDraftPlacement | null>(null)
  const [inspectorDirty, setInspectorDirty] = useState(false)
  const [blockDragActive, setBlockDragActive] = useState(false)
  const timelineRef = useRef<HTMLDivElement>(null)

  const load = useCallback(async () => {
    if (!date) return
    setLoading(true)
    setError(null)
    try {
      const [d, tt] = await Promise.all([api.getDay(date), api.listTaskTypes()])
      setDay(d)
      setTaskTypes(tt)
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

  const onLaneSlotClick = useCallback(
    (lane: BlockLane, startMin: number, endMin: number) => {
      if (!tryDiscardIfNeeded()) return
      setDraft({ lane, start_minute: startMin, end_minute: endMin })
      setSelectedBlockId(null)
    },
    [tryDiscardIfNeeded],
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
      if (!date || !draft) return
      setSaveState('saving')
      setError(null)
      try {
        const next = await api.createBlock(date, {
          lane: draft.lane,
          task_type_id: payload.task_type_id,
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
        setDraft(null)
        if (created) setSelectedBlockId(created.id)
        setSaveState('saved')
      } catch (e) {
        setSaveState('error')
        setError(e instanceof Error ? e.message : 'Failed to create block')
        throw e
      }
    },
    [date, draft],
  )

  const patchBlock = useCallback(
    async (
      blockId: number,
      patch: {
        task_type_id?: number
        note?: string | null
        start_minute?: number
        end_minute?: number
      },
    ) => {
      if (!date) return
      setSaveState('saving')
      setError(null)
      try {
        const next = await api.patchBlock(date, blockId, patch)
        setDay(next)
        setSaveState('saved')
      } catch (e) {
        setSaveState('error')
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
      setSaveState('saving')
      setError(null)
      try {
        const next = await api.deleteBlock(date, blockId)
        setDay(next)
        setSaveState('saved')
      } catch (e) {
        setSaveState('error')
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
      setSaveState('saving')
      setError(null)
      try {
        const next = await api.completeBlockAsPlanned(date, blockId)
        setDay(next)
        setSaveState('saved')
      } catch (e) {
        setSaveState('error')
        const msg = e instanceof Error ? e.message : 'Failed to complete block'
        setError(msg)
        throw e
      }
    },
    [date],
  )

  const createTaskTypePath = useCallback(async (name: string) => {
    setSaveState('saving')
    setError(null)
    try {
      const created = await api.createTaskType({ name })
      const nextTaskTypes = await api.listTaskTypes()
      setTaskTypes(nextTaskTypes)
      setSaveState('saved')
      return created
    } catch (e) {
      setSaveState('error')
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

  return (
    <Layout mainClassName="mx-auto w-full max-w-none px-6 py-12 lg:px-8 xl:px-10">
      <div className="flex flex-col gap-8 lg:flex-row lg:items-start lg:gap-0">
        <div className="min-w-0 flex-1 lg:pr-4">
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
                  Timezone {day.meta.timezone}. Server today {day.meta.today}.
                </p>
                <p className="mt-2 text-xs text-outline">
                  <span
                    className={
                      saveState === 'error'
                        ? 'text-error'
                        : saveState === 'saving'
                          ? 'text-tertiary'
                          : saveState === 'saved'
                            ? 'text-tertiary'
                            : 'text-outline'
                    }
                  >
                    {saveState === 'saving' && 'Saving…'}
                    {saveState === 'saved' && 'Saved'}
                    {saveState === 'error' && 'Save failed'}
                    {saveState === 'idle' && '\u00a0'}
                  </span>
                </p>
              </div>
              <div
                className="flex flex-wrap items-center gap-2"
                data-testid="day-nav"
              >
                <button
                  type="button"
                  className="rounded-full border border-outline-variant/40 px-3 py-1.5 font-headline text-sm text-on-surface hover:bg-surface-container-high"
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
                  className="rounded-full border border-outline-variant/40 px-3 py-1.5 font-headline text-sm text-on-surface hover:bg-surface-container-high"
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
              onBlockClick={(blockId, _lane) => onBlockClick(blockId)}
              onBlockDragSessionChange={setBlockDragActive}
            />
          </section>
        </div>

        {/* Desktop: persistent inspector rail */}
        <div
          className="hidden w-full shrink-0 lg:block lg:w-[min(28rem,100%)] lg:max-w-md lg:self-start lg:pl-6"
          data-testid="day-inspector-rail"
        >
          <aside
            role="complementary"
            aria-label="Block details"
            data-inspector="rail"
            className={`sticky top-24 mt-0 w-full bg-surface-container-low dark:bg-stone-900${blockDragActive ? ' pointer-events-none' : ''}`}
          >
            {selectedBlock == null && draft == null ? (
              <div className="rounded-2xl bg-surface-container-lowest/90 px-4 pb-6 pt-6 shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-[20px] transition-opacity duration-150 dark:bg-stone-950/85 dark:shadow-[0_0_40px_rgba(0,0,0,0.25)] lg:rounded-none lg:bg-transparent lg:px-0 lg:pb-6 lg:pt-6 lg:shadow-none lg:backdrop-blur-none">
                <h2 className="font-headline text-sm font-light tracking-wide text-on-surface-variant">
                  Details
                </h2>
                <p className="mt-2 font-headline text-lg font-extralight text-on-surface">
                  Select a block to edit
                </p>
                <p className="mt-1 font-body text-xs text-on-surface-variant">
                  Click an empty slot to create
                </p>
              </div>
            ) : (
              <div className="transition-opacity duration-150">
                <TimeBlockInspectorContent
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
    </Layout>
  )
}
