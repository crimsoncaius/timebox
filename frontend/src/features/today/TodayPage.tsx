import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { DayCalendarPopover } from '../../components/DayCalendarPopover'
import { DayTimeline } from '../../components/DayTimeline'
import { Layout } from '../../components/Layout'
import { TimeBlockModal } from '../../components/TimeBlockModal'
import { api, type BlockLane, type DayRead, type TaskType } from '../../lib/api'
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

function defaultTaskTypeId(types: TaskType[]): number | null {
  if (types.length === 0) return null
  return [...types].sort((a, b) => a.id - b.id)[0]!.id
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

  const createBlock = useCallback(
    async (lane: BlockLane, startMin: number, endMin: number) => {
      if (!date) return
      const tid = defaultTaskTypeId(taskTypes)
      if (tid === null) {
        setSaveState('error')
        setError('Add at least one task type on the Task types page before creating blocks.')
        return
      }
      setSaveState('saving')
      setError(null)
      try {
        const next = await api.createBlock(date, {
          lane,
          task_type_id: tid,
          start_minute: startMin,
          end_minute: endMin,
        })
        setDay(next)
        setSaveState('saved')
      } catch (e) {
        setSaveState('error')
        setError(e instanceof Error ? e.message : 'Failed to create block')
      }
    },
    [date, taskTypes],
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

  const detailOpen = selectedBlock != null

  return (
    <Layout
      mainClassName={
        detailOpen
          ? 'mx-auto w-full max-w-none px-6 py-12 lg:px-8 xl:px-10'
          : undefined
      }
    >
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
              day={day}
              readOnly={false}
              onCreateBlock={createBlock}
              onPatchBlock={patchBlock}
              onBlockClick={(blockId) => setSelectedBlockId(blockId)}
            />
          </section>
        </div>

        <TimeBlockModal
          open={selectedBlock != null}
          block={selectedBlock}
          day={day}
          taskTypes={taskTypes}
          onClose={() => setSelectedBlockId(null)}
          onSave={(patch) => {
            if (!selectedBlock) return Promise.resolve()
            return patchBlock(selectedBlock.id, patch)
          }}
          onDelete={() => {
            if (!selectedBlock) return Promise.resolve()
            return deleteBlock(selectedBlock.id)
          }}
          onCompleteAsPlanned={
            selectedBlock?.lane === 'planned'
              ? () => {
                  if (!selectedBlock) return Promise.resolve()
                  return completeBlockAsPlanned(selectedBlock.id)
                }
              : undefined
          }
          onCreateTaskTypePath={createTaskTypePath}
        />
      </div>
    </Layout>
  )
}
