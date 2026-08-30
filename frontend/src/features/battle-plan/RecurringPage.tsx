import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import {
  api,
  type BattleTask,
  type PriorityLevel,
  type Project,
  type ProjectWrite,
  type RecurrenceFrequency,
  type RecurrenceMode,
  type RecurrencePreview,
  type RecurrenceRuleWrite,
  type RecurrenceStatus,
  type RecurringTemplate,
  type RecurringTemplateWrite,
  type TaskType,
} from '../../lib/api'
import { BattlePlanSidebar } from './BattlePlanSidebar'
import { persistBattlePlanScope, type BattlePlanScope } from './battlePlanState'
import { ProjectEditor } from './ProjectEditor'
import { PriorityControl } from './TaskDetailPanel'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const buttonClass = 'rounded-xl px-3.5 py-2 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary/30'

function displayDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${value}T12:00:00Z`))
}

function displayWindow(start: string, end: string) {
  if (start === end) return displayDate(start)
  const startDate = new Date(`${start}T12:00:00Z`)
  const endDate = new Date(`${end}T12:00:00Z`)
  const formatter = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
  return `${formatter.format(startDate)}–${formatter.format(endDate)}`
}

function projectTaskCount(tasks: BattleTask[], projectId: number) {
  return tasks.reduce(
    (count, task) => count + (task.project_id === projectId ? 1 + task.subtasks.length : 0),
    0,
  )
}

export function RecurringPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState<RecurrenceStatus>('active')
  const [templates, setTemplates] = useState<RecurringTemplate[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [taskTypes, setTaskTypes] = useState<TaskType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<RecurringTemplate | null>(null)
  const [mobileSidebar, setMobileSidebar] = useState(false)
  const [projectEditor, setProjectEditor] = useState<Project | null | undefined>(undefined)
  const [projectEditorCount, setProjectEditorCount] = useState(0)
  const [timezone, setTimezone] = useState('UTC')
  const [applicationToday, setApplicationToday] = useState<string | null>(null)
  const latestLoadRequest = useRef(0)
  const selectedId = Number(searchParams.get('recurring')) || null
  const selected = templates.find((template) => template.id === selectedId) ?? null
  const formApplicationToday = editing?.start_date ?? applicationToday

  const load = useCallback(async (nextStatus: RecurrenceStatus) => {
    const requestId = ++latestLoadRequest.current
    setLoading(true)
    setError(null)
    try {
      const [rows, projectRows, typeRows] = await Promise.all([
        api.listRecurringTemplates(nextStatus), api.listProjects(), api.listTaskTypes(),
      ])
      if (requestId !== latestLoadRequest.current) return
      setTemplates(rows)
      setProjects(projectRows)
      setTaskTypes(typeRows)
    } catch (cause) {
      if (requestId !== latestLoadRequest.current) return
      setError(cause instanceof Error ? cause.message : 'Failed to load recurring templates')
    } finally {
      if (requestId === latestLoadRequest.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load('active')
    return () => { latestLoadRequest.current += 1 }
  }, [load])

  useEffect(() => {
    let active = true
    void api.health().then((health) => {
      if (!active) return
      setTimezone(health.timezone)
      setApplicationToday(health.today)
    }).catch(() => undefined)
    return () => { active = false }
  }, [])

  const lifecycle = async (template: RecurringTemplate, action: 'pause' | 'resume' | 'end') => {
    setError(null)
    try {
      if (action === 'pause') await api.pauseRecurringTemplate(template.id)
      if (action === 'resume') await api.resumeRecurringTemplate(template.id)
      if (action === 'end') {
        if (!window.confirm(`End “${template.title}”? Future pristine tasks will be removed.`)) return
        await api.endRecurringTemplate(template.id)
      }
      setSearchParams({ view: 'recurring' })
      await load(status)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Recurring action failed')
    }
  }

  const openBattlePlanScope = (scope: BattlePlanScope) => {
    persistBattlePlanScope(scope)
    navigate('/battle-plan')
  }

  const openProject = async (project: Project) => {
    setProjectEditor(project)
    try {
      const [active, archived, trash] = await Promise.all([
        api.listBattleTasks('active'), api.listBattleTasks('archived'), api.listBattleTasks('trash'),
      ])
      setProjectEditorCount(projectTaskCount([...active.items, ...archived.items, ...trash.items], project.id))
    } catch {
      setProjectEditorCount(0)
    }
  }

  return (
    <Layout mainClassName="w-full px-4 py-6 sm:px-8 lg:px-10">
      <div className="mb-6 flex items-center justify-between lg:hidden">
        <button type="button" className="rounded-xl bg-surface-container-low px-3 py-2 text-sm dark:bg-dark-surface-container" onClick={() => setMobileSidebar(true)}>Lists & projects</button>
      </div>
      <div className="flex min-h-[calc(100vh-9rem)] gap-6">
        <BattlePlanSidebar
          open={mobileSidebar}
          collection="active"
          scope="all"
          recurring
          projects={projects}
          onClose={() => setMobileSidebar(false)}
          onScope={openBattlePlanScope}
          onCollection={(collection) => navigate(`/battle-plan?collection=${collection}`)}
          onNewProject={() => { setProjectEditorCount(0); setProjectEditor(null) }}
          onEditProject={(project) => void openProject(project)}
        />

        <section className="min-w-0 max-w-6xl flex-1">
          <header className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="font-label text-xs uppercase tracking-[0.18em] text-on-surface-variant">Battle Plan</p>
            <h1 className="mt-2 font-headline text-[2.5rem] font-extralight leading-none tracking-tighter">Recurring</h1>
            <p className="mt-3 max-w-2xl text-sm leading-relaxed text-on-surface-variant">Templates create independent Battle Plan tasks seven days ahead.</p>
          </div>
          <button type="button" disabled={!applicationToday} className={`${buttonClass} bg-primary text-on-primary hover:bg-primary-dim disabled:cursor-wait disabled:opacity-50`} onClick={() => { if (applicationToday) setCreating(true) }}>
            New recurring task
          </button>
          </header>

        <div className="mt-8 flex gap-1 rounded-xl bg-surface-container-low p-1 sm:w-fit dark:bg-dark-surface-container-low">
          {(['active', 'paused', 'ended'] as RecurrenceStatus[]).map((value) => (
            <button key={value} type="button" onClick={() => { setStatus(value); setSearchParams({ view: 'recurring' }); void load(value) }} className={`${buttonClass} capitalize ${status === value ? 'bg-surface-container-lowest shadow-sm dark:bg-dark-surface-container-high' : 'text-on-surface-variant'}`}>
              {value[0].toUpperCase() + value.slice(1)}
            </button>
          ))}
        </div>

        {error ? <div role="alert" className="mt-5 rounded-xl bg-error-container/20 px-4 py-3 text-sm text-on-error-container">{error}</div> : null}
        {loading ? <p className="mt-8 text-on-surface-variant">Loading recurring templates…</p> : null}
        {!loading && templates.length === 0 ? (
          <div className="mt-8 rounded-2xl border border-dashed border-outline-variant/30 px-6 py-14 text-center">
            <p className="font-headline text-lg font-light">No {status} templates</p>
            <p className="mt-2 text-sm text-on-surface-variant">Create a schedule or period quota to populate this view.</p>
          </div>
        ) : null}

        <div className="mt-6 overflow-hidden rounded-2xl border border-outline-variant/15 dark:border-dark-outline-variant/30">
          {templates.map((template) => (
            <TemplateRow
              key={template.id}
              template={template}
              selected={template.id === selectedId}
              onSelect={() => setSearchParams({ view: 'recurring', recurring: String(template.id) })}
              onEdit={() => setEditing(template)}
              onLifecycle={(action) => void lifecycle(template, action)}
            />
          ))}
        </div>
        </section>
      </div>

      {selected ? <TemplateDetail template={selected} onClose={() => setSearchParams({ view: 'recurring' })} onEdit={() => setEditing(selected)} /> : null}
      {(creating || editing) && formApplicationToday ? (
        <TemplateForm
          initialMode={editing?.mode ?? 'scheduled'}
          template={editing}
          applicationToday={formApplicationToday}
          projects={projects}
          taskTypes={taskTypes}
          onClose={() => { setCreating(false); setEditing(null) }}
          onSaved={async (saved) => {
            setCreating(false)
            setEditing(null)
            setStatus(saved.status)
            setSearchParams({ view: 'recurring', recurring: String(saved.id) })
            await load(saved.status)
          }}
        />
      ) : null}
      {projectEditor !== undefined ? (
        <ProjectEditor
          project={projectEditor}
          timezone={timezone}
          taskCount={projectEditorCount}
          onClose={() => setProjectEditor(undefined)}
          onSave={async (body: ProjectWrite) => {
            if (projectEditor) await api.patchProject(projectEditor.id, body)
            else await api.createProject(body)
            setProjects(await api.listProjects())
            setProjectEditor(undefined)
          }}
          onDelete={projectEditor ? async () => {
            await api.deleteProject(projectEditor.id)
            setProjects(await api.listProjects())
            setProjectEditor(undefined)
          } : null}
        />
      ) : null}
    </Layout>
  )
}

function TemplateRow({ template, selected, onSelect, onEdit, onLifecycle }: {
  template: RecurringTemplate
  selected: boolean
  onSelect: () => void
  onEdit: () => void
  onLifecycle: (action: 'pause' | 'resume' | 'end') => void
}) {
  return (
    <article className={`grid gap-3 border-b border-outline-variant/15 bg-surface-container-lowest px-4 py-4 last:border-b-0 sm:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)_minmax(8rem,.7fr)_auto] sm:items-center dark:border-dark-outline-variant/30 dark:bg-dark-surface-container-lowest ${selected ? 'ring-1 ring-inset ring-primary/25' : ''}`}>
      <button type="button" aria-label={template.title} className="min-w-0 text-left" onClick={onSelect}>
        <span className="block truncate font-headline text-base">{template.title}</span>
        <span className="mt-1 block truncate text-xs text-on-surface-variant">{template.project?.name ?? 'Admin'}{template.task_type ? ` · ${template.task_type.name}` : ''}</span>
      </button>
      <button type="button" className="text-left text-sm text-on-surface-variant" onClick={onSelect}>{template.cadence}</button>
      <button type="button" className="text-left text-xs text-on-surface-variant" onClick={onSelect}>
        <span className="block font-label uppercase tracking-wide">Next</span>
        <span className="mt-1 block text-sm text-on-surface">{displayDate(template.next_occurrence)}</span>
      </button>
      <div className="flex flex-wrap items-center justify-end gap-1.5">
        <span className="rounded-full bg-surface-container px-2 py-1 text-[10px] uppercase tracking-wide text-on-surface-variant dark:bg-dark-surface-container">{template.status}</span>
        <button type="button" className={`${buttonClass} px-2.5 py-1.5 text-xs text-on-surface-variant hover:bg-surface-container-low`} onClick={onEdit}>Edit</button>
        {template.status === 'active' ? <button type="button" className={`${buttonClass} px-2.5 py-1.5 text-xs hover:bg-surface-container-low`} onClick={() => onLifecycle('pause')}>Pause</button> : null}
        {template.status === 'paused' ? <button type="button" className={`${buttonClass} px-2.5 py-1.5 text-xs hover:bg-surface-container-low`} onClick={() => onLifecycle('resume')}>Resume</button> : null}
        {template.status !== 'ended' ? <button type="button" className={`${buttonClass} px-2.5 py-1.5 text-xs text-error hover:bg-error-container/10`} onClick={() => onLifecycle('end')}>End</button> : null}
      </div>
    </article>
  )
}

function TemplateDetail({ template, onClose, onEdit }: { template: RecurringTemplate; onClose: () => void; onEdit: () => void }) {
  return (
    <div className="fixed inset-0 z-90 flex justify-end bg-black/25" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside role="dialog" aria-modal="true" className="h-full w-full max-w-lg overflow-y-auto bg-surface p-6 shadow-2xl dark:bg-dark-background" aria-label={`Recurring template ${template.title}`}>
        <div className="flex items-start justify-between gap-4">
          <div><p className="font-label text-xs uppercase tracking-[0.16em] text-on-surface-variant">{template.status}</p><h2 className="mt-2 font-headline text-3xl font-light">{template.title}</h2></div>
          <button type="button" aria-label="Close recurring details" onClick={onClose} className="rounded-full p-2 hover:bg-surface-container-low">×</button>
        </div>
        {template.description ? <p className="mt-5 whitespace-pre-wrap text-sm leading-relaxed text-on-surface-variant">{template.description}</p> : null}
        <dl className="mt-7 grid grid-cols-2 gap-4 rounded-2xl bg-surface-container-low p-4 text-sm dark:bg-dark-surface-container-low">
          <div><dt className="text-xs text-on-surface-variant">Cadence</dt><dd className="mt-1">{template.cadence}</dd></div>
          <div><dt className="text-xs text-on-surface-variant">Location</dt><dd className="mt-1">{template.project?.name ?? 'Admin'}</dd></div>
          <div><dt className="text-xs text-on-surface-variant">Starts</dt><dd className="mt-1">{displayDate(template.start_date)}</dd></div>
          <div><dt className="text-xs text-on-surface-variant">Ends</dt><dd className="mt-1">{template.end_date ? displayDate(template.end_date) : template.cycle_limit ? `${template.cycle_limit} cycles` : 'Never'}</dd></div>
        </dl>
        <section className="mt-8"><h3 className="font-headline text-lg font-light">Next five</h3><div className="mt-3 space-y-2">{template.upcoming.map((window) => <div key={window.key} className="rounded-xl bg-surface-container-low px-3 py-2 text-sm dark:bg-dark-surface-container-low">{displayWindow(window.start, window.end)}</div>)}</div></section>
        {template.current_tasks.length ? <section className="mt-8"><h3 className="font-headline text-lg font-light">Current and overdue tasks</h3><div className="mt-3 space-y-2">{template.current_tasks.map((task) => <Link key={task.id} to={`/battle-plan?task=${task.id}`} className="flex items-center justify-between rounded-xl bg-surface-container-low px-3 py-2 text-sm hover:bg-surface-container-high dark:bg-dark-surface-container-low"><span>{task.title}</span><span className={task.overdue ? 'text-error' : 'text-on-surface-variant'}>{task.overdue ? 'Overdue' : displayDate(task.deadline_date)}</span></Link>)}</div></section> : null}
        <button type="button" className={`${buttonClass} mt-8 bg-primary text-on-primary`} onClick={onEdit}>Edit template</button>
      </aside>
    </div>
  )
}

type RecurrencePreset = 'daily' | 'weekdays' | 'weekly' | 'monthly' | 'custom' | 'day' | 'week' | 'month'

const SCHEDULE_PRESETS: Array<{ id: RecurrencePreset; label: string }> = [
  { id: 'daily', label: 'Daily' },
  { id: 'weekdays', label: 'Weekdays' },
  { id: 'weekly', label: 'Weekly' },
  { id: 'monthly', label: 'Monthly' },
  { id: 'custom', label: 'Custom…' },
]

const QUOTA_PRESETS: Array<{ id: RecurrencePreset; label: string }> = [
  { id: 'day', label: 'Per day' },
  { id: 'week', label: 'Per week' },
  { id: 'month', label: 'Per month' },
]

const recurringFieldClass = 'w-full rounded-[10px] border border-[var(--task-detail-input-border)] bg-[var(--task-detail-input-surface)] px-3 py-[9px] text-sm text-[var(--task-detail-primary)] outline-none transition-colors duration-120 ease-out hover:border-[var(--task-detail-input-hover)] focus-visible:border-[var(--task-detail-input-hover)] focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]'

function weekdayIndexForIsoDate(value: string) {
  const [year, month, dayOfMonth] = value.split('-').map(Number)
  const day = new Date(Date.UTC(year, month - 1, dayOfMonth)).getUTCDay()
  return day === 0 ? 6 : day - 1
}

function dayOfMonthForIsoDate(value: string) {
  return Number(value.slice(8, 10))
}

function initialPreset(mode: RecurrenceMode, frequency: RecurrenceFrequency, interval: number, weekdays: number[]): RecurrencePreset {
  if (mode === 'quota') return frequency === 'daily' ? 'day' : frequency === 'monthly' ? 'month' : 'week'
  if (interval !== 1) return 'custom'
  if (frequency === 'daily') return 'daily'
  if (frequency === 'monthly') return 'monthly'
  if (weekdays.length === 5 && weekdays.every((day, index) => day === index)) return 'weekdays'
  return 'weekly'
}

function recurrenceSummary({
  mode, frequency, interval, weekdays, monthDay, quotaCount, startDate, ending,
}: {
  mode: RecurrenceMode
  frequency: RecurrenceFrequency
  interval: number
  weekdays: number[]
  monthDay: number
  quotaCount: number
  startDate: string
  ending: 'never' | 'date' | 'cycles'
}) {
  const start = startDate ? displayDate(startDate) : 'the selected date'
  const endingText = ending === 'never' ? 'forever' : ending === 'date' ? 'until an end date' : 'for a set number of cycles'
  const unit = frequency === 'daily' ? 'day' : frequency === 'weekly' ? 'week' : 'month'

  if (mode === 'quota') {
    const cadence = quotaCount === 1 ? 'Once' : `${quotaCount} times`
    return `${cadence} per calendar ${unit}, starting ${start}, ${endingText}.`
  }

  let cadence = interval === 1 ? `Every ${unit}` : `Every ${interval} ${unit}s`
  if (frequency === 'weekly' && weekdays.length > 0) cadence += ` on ${weekdays.map((day) => WEEKDAYS[day]).join(', ')}`
  if (frequency === 'monthly') cadence += ` on day ${monthDay}`
  return `${cadence}, starting ${start}, ${endingText}.`
}

function TemplateForm({ initialMode, template, applicationToday, projects, taskTypes, onClose, onSaved }: {
  initialMode: RecurrenceMode
  template: RecurringTemplate | null
  applicationToday: string
  projects: Project[]
  taskTypes: TaskType[]
  onClose: () => void
  onSaved: (template: RecurringTemplate) => Promise<void>
}) {
  const [mode, setMode] = useState<RecurrenceMode>(initialMode)
  const [title, setTitle] = useState(template?.title ?? '')
  const [description, setDescription] = useState(template?.description ?? '')
  const [projectId, setProjectId] = useState(template?.project_id ? String(template.project_id) : '')
  const [taskTypeId, setTaskTypeId] = useState(template?.task_type_id ? String(template.task_type_id) : '')
  const [urgency, setUrgency] = useState<PriorityLevel | null>(template?.urgency ?? null)
  const [importance, setImportance] = useState<PriorityLevel | null>(template?.importance ?? null)
  const [frequency, setFrequency] = useState<RecurrenceFrequency>(template?.frequency ?? 'daily')
  const [interval, setInterval] = useState(template?.interval ?? 1)
  const [weekdays, setWeekdays] = useState<number[]>(template?.weekdays ?? [weekdayIndexForIsoDate(applicationToday)])
  const [monthDay, setMonthDay] = useState(template?.month_day ?? dayOfMonthForIsoDate(applicationToday))
  const [quotaCount, setQuotaCount] = useState(template?.quota_count ?? 3)
  const [startDate, setStartDate] = useState(template?.start_date ?? applicationToday)
  const [ending, setEnding] = useState<'never' | 'date' | 'cycles'>(template?.end_date ? 'date' : template?.cycle_limit ? 'cycles' : 'never')
  const [endDate, setEndDate] = useState(template?.end_date ?? '')
  const [cycleLimit, setCycleLimit] = useState(template?.cycle_limit ?? 10)
  const [checklist, setChecklist] = useState(template?.checklist_items.map((item) => item.title).join('\n') ?? '')
  const [preview, setPreview] = useState<RecurrencePreview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [preset, setPreset] = useState<RecurrencePreset>(() => initialPreset(
    initialMode,
    template?.frequency ?? 'daily',
    template?.interval ?? 1,
    template?.weekdays ?? [weekdayIndexForIsoDate(applicationToday)],
  ))

  const isDirty = useMemo(() => (
    mode !== initialMode
    || title !== (template?.title ?? '')
    || description !== (template?.description ?? '')
    || projectId !== (template?.project_id ? String(template.project_id) : '')
    || taskTypeId !== (template?.task_type_id ? String(template.task_type_id) : '')
    || urgency !== (template?.urgency ?? null)
    || importance !== (template?.importance ?? null)
    || frequency !== (template?.frequency ?? 'daily')
    || interval !== (template?.interval ?? 1)
    || JSON.stringify(weekdays) !== JSON.stringify(template?.weekdays ?? [weekdayIndexForIsoDate(applicationToday)])
    || monthDay !== (template?.month_day ?? dayOfMonthForIsoDate(applicationToday))
    || quotaCount !== (template?.quota_count ?? 3)
    || startDate !== (template?.start_date ?? applicationToday)
    || ending !== (template?.end_date ? 'date' : template?.cycle_limit ? 'cycles' : 'never')
    || endDate !== (template?.end_date ?? '')
    || cycleLimit !== (template?.cycle_limit ?? 10)
    || checklist !== (template?.checklist_items.map((item) => item.title).join('\n') ?? '')
  ), [applicationToday, checklist, cycleLimit, description, endDate, ending, frequency, importance, initialMode, interval, mode, monthDay, projectId, quotaCount, startDate, taskTypeId, template, title, urgency, weekdays])

  const requestClose = useCallback(() => {
    if (isDirty && !window.confirm('Discard your unsaved changes?')) return
    onClose()
  }, [isDirty, onClose])

  const rule = useMemo<RecurrenceRuleWrite>(() => ({
    mode, frequency, interval: mode === 'quota' ? 1 : interval,
    weekdays: mode === 'scheduled' && frequency === 'weekly' ? weekdays : [],
    month_day: mode === 'scheduled' && frequency === 'monthly' ? monthDay : null,
    quota_count: mode === 'quota' ? quotaCount : null,
    start_date: startDate,
    end_date: ending === 'date' ? endDate || null : null,
    cycle_limit: ending === 'cycles' ? cycleLimit : null,
  }), [mode, frequency, interval, weekdays, monthDay, quotaCount, startDate, ending, endDate, cycleLimit])

  useEffect(() => {
    if (!startDate || (frequency === 'weekly' && mode === 'scheduled' && weekdays.length === 0) || (ending === 'date' && !endDate)) { setPreview(null); return }
    let active = true
    const timer = window.setTimeout(() => {
      void api.previewRecurrence(rule).then((value) => { if (active) setPreview(value) }).catch(() => { if (active) setPreview(null) })
    }, 180)
    return () => { active = false; window.clearTimeout(timer) }
  }, [rule, startDate, frequency, mode, weekdays.length, ending, endDate])

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') requestClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [requestClose])

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  const switchMode = (nextMode: RecurrenceMode) => {
    if (template || nextMode === mode) return
    setMode(nextMode)
    setInterval(1)
    if (nextMode === 'scheduled') {
      setFrequency('daily')
      setPreset('daily')
    } else {
      setFrequency('weekly')
      setPreset('week')
    }
  }

  const choosePreset = (nextPreset: RecurrencePreset) => {
    setPreset(nextPreset)
    if (nextPreset === 'custom') return
    setInterval(1)
    if (nextPreset === 'daily' || nextPreset === 'day') setFrequency('daily')
    if (nextPreset === 'weekly' || nextPreset === 'weekdays' || nextPreset === 'week') setFrequency('weekly')
    if (nextPreset === 'monthly' || nextPreset === 'month') setFrequency('monthly')
    if (nextPreset === 'weekdays') setWeekdays([0, 1, 2, 3, 4])
    if (nextPreset === 'weekly' && weekdays.length === 0) setWeekdays([weekdayIndexForIsoDate(applicationToday)])
  }

  const summary = recurrenceSummary({
    mode, frequency, interval, weekdays, monthDay, quotaCount, startDate, ending,
  })

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!title.trim()) return
    setSaving(true)
    setError(null)
    try {
      const currentPreview = await api.previewRecurrence(rule)
      let confirmBackfill = false
      if (!template && currentPreview.past_cycles > 0) {
        confirmBackfill = window.confirm(`This will backfill ${currentPreview.past_cycles} past cycles (${currentPreview.past_tasks} tasks). Continue?`)
        if (!confirmBackfill) return
      }
      const body: RecurringTemplateWrite = {
        ...rule, title: title.trim(), description, project_id: projectId ? Number(projectId) : null,
        task_type_id: taskTypeId ? Number(taskTypeId) : null,
        urgency, importance,
        checklist_titles: mode === 'scheduled' ? checklist.split('\n').map((value) => value.trim()).filter(Boolean) : [],
        confirm_backfill: confirmBackfill,
      }
      const saved = template
        ? await api.patchRecurringTemplate(template.id, body)
        : await api.createRecurringTemplate(body)
      await onSaved(saved)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Failed to save recurring template')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-100 flex items-center justify-center overflow-hidden bg-black/35 p-3" onMouseDown={(event) => { if (event.target === event.currentTarget) requestClose() }}>
      <form role="dialog" aria-modal="true" aria-label={template ? `Edit ${template.title}` : 'New recurring task'} onSubmit={(event) => void submit(event)} className="task-detail-dialog max-h-[94vh] w-full max-w-[640px] overflow-y-auto rounded-3xl border border-[var(--task-detail-border)] bg-surface text-[var(--task-detail-primary)] shadow-[var(--task-detail-shadow)] dark:bg-dark-background">
        <header className="flex items-start justify-between gap-4 px-5 pt-[26px] pb-[18px] sm:px-7">
          <div>
            <p className="font-label text-[11px] uppercase tracking-[0.18em] text-[var(--task-detail-muted)]">Recurring template</p>
            <h2 className="mt-2 font-headline text-[28px] font-light tracking-[-0.02em]">{template ? template.title : 'New recurring task'}</h2>
          </div>
          <button type="button" aria-label="Close recurring form" onClick={requestClose} className="border-0 bg-transparent p-1.5 text-[var(--task-detail-secondary)] transition-colors duration-120 ease-out hover:text-[var(--task-detail-primary)] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]">
            <span className="material-symbols-outlined text-[20px]" aria-hidden>close</span>
          </button>
        </header>

        <div role="radiogroup" aria-label="Recurrence mode" aria-disabled={Boolean(template)} className="mx-5 mb-[22px] flex w-fit gap-1 rounded-full bg-[var(--task-detail-track)] p-1 sm:mx-7">
          {([
            ['scheduled', 'On a schedule'],
            ['quota', 'Times per period'],
          ] as const).map(([value, label]) => {
            const selected = mode === value
            return (
              <button key={value} type="button" role="radio" aria-checked={selected} disabled={Boolean(template)} onClick={() => switchMode(value)} className={`rounded-full border-0 px-4 py-[7px] text-[13px] transition-colors duration-120 ease-out focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)] disabled:cursor-default ${selected ? 'bg-[var(--task-detail-input-surface)] font-medium text-[var(--task-detail-primary)] shadow-[0_1px_2px_rgba(45,52,53,0.08)]' : 'bg-transparent font-normal text-[var(--task-detail-muted)] hover:text-[var(--task-detail-primary)]'}`}>
                {label}
              </button>
            )
          })}
        </div>

        {error ? <div role="alert" className="mx-5 mb-5 rounded-xl bg-error-container/20 px-4 py-3 text-sm text-on-error-container sm:mx-7">{error}</div> : null}

        <section className="border-t border-[var(--task-detail-divider)] px-5 py-[18px] sm:px-7 sm:py-[22px]">
          <SectionHeading>The task</SectionHeading>
          <input autoFocus required aria-label="Title" placeholder="Untitled recurring task" value={title} onChange={(event) => setTitle(event.target.value)} className="w-full border-0 bg-transparent p-0 font-headline text-2xl font-light tracking-[-0.02em] text-[var(--task-detail-primary)] outline-none placeholder:text-[var(--task-detail-title-placeholder)]" />
          <textarea rows={2} aria-label="Description" placeholder="Notes and context" value={description} onChange={(event) => setDescription(event.target.value)} className="mt-3.5 w-full resize-y border-0 border-l-2 border-l-[var(--color-paper-rule)] bg-transparent py-0.5 pr-0 pl-3.5 text-sm leading-[1.7] text-[var(--task-detail-primary)] outline-none placeholder:text-[var(--task-detail-muted)]" />
          <div className="mt-[18px] grid gap-3.5 sm:grid-cols-2">
            <Select label="Location" value={projectId} unset={!projectId} onChange={setProjectId}><option value="">Admin</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</Select>
            <Select label="Task type" value={taskTypeId} unset={!taskTypeId} onChange={setTaskTypeId}><option value="">Unset</option>{taskTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</Select>
          </div>
        </section>

        <section className="border-t border-[var(--task-detail-divider)] px-5 py-[18px] sm:px-7 sm:py-[22px]">
          <SectionHeading>{mode === 'scheduled' ? 'The schedule' : 'The quota'}</SectionHeading>
          <div className="flex flex-wrap gap-2">
            {(mode === 'scheduled' ? SCHEDULE_PRESETS : QUOTA_PRESETS).map((item) => {
              const selected = preset === item.id
              return (
                <button key={item.id} type="button" aria-pressed={selected} onClick={() => choosePreset(item.id)} className={`rounded-full px-3.5 py-[7px] text-[13px] transition-colors duration-120 ease-out focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)] ${selected ? 'border border-transparent bg-[var(--task-detail-selected)] text-[var(--task-detail-primary)]' : 'border border-[var(--task-detail-border)] bg-transparent text-[var(--task-detail-secondary)] hover:border-[var(--task-detail-input-hover)] hover:text-[var(--task-detail-primary)]'}`}>
                  {item.label}
                </button>
              )
            })}
          </div>

          <div className="mt-4 grid gap-3.5 sm:grid-cols-3">
            {mode === 'scheduled' ? (
              <Field label="Every">
                <div className="flex items-center gap-2">
                  <input aria-label="Interval" type="number" min={1} value={interval} onChange={(event) => { setInterval(Number(event.target.value)); setPreset('custom') }} className={recurringFieldClass} />
                  <span className="text-[13px] text-[var(--task-detail-muted)]">{{ daily: 'days', weekly: 'weeks', monthly: 'months' }[frequency]}</span>
                </div>
              </Field>
            ) : (
              <Field label="Times per period">
                <div className="flex items-center gap-2">
                  <input aria-label="Times per period" type="number" min={1} max={100} value={quotaCount} onChange={(event) => setQuotaCount(Number(event.target.value))} className={recurringFieldClass} />
                  <span className="text-[13px] text-[var(--task-detail-muted)]">times</span>
                </div>
              </Field>
            )}
            <Field label="Starts"><input aria-label="Start date" required type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className={recurringFieldClass} /></Field>
            <Select label="Ends" ariaLabel="Ending" value={ending} onChange={(value) => setEnding(value as typeof ending)}><option value="never">Never</option><option value="date">On date</option><option value="cycles">After cycles</option></Select>
            {ending === 'date' ? <Field label="Inclusive end date"><input aria-label="Inclusive end date" required type="date" min={startDate} value={endDate} onChange={(event) => setEndDate(event.target.value)} className={recurringFieldClass} /></Field> : null}
            {ending === 'cycles' ? <Field label="Cycle limit"><input aria-label="Cycle limit" type="number" min={1} value={cycleLimit} onChange={(event) => setCycleLimit(Number(event.target.value))} className={recurringFieldClass} /></Field> : null}
          </div>

          {mode === 'scheduled' && frequency === 'weekly' ? (
            <fieldset className="mt-4 border-0 p-0">
              <legend className="mb-2 p-0 text-xs text-[var(--task-detail-muted)]">Weekdays</legend>
              <div className="flex flex-wrap gap-2">
                {WEEKDAYS.map((day, index) => {
                  const selected = weekdays.includes(index)
                  return (
                    <button key={day} type="button" aria-label={day} aria-pressed={selected} onClick={() => { setWeekdays((current) => current.includes(index) ? current.filter((value) => value !== index) : [...current, index].sort()); setPreset('custom') }} className={`size-[38px] rounded-[10px] text-xs transition-colors duration-120 ease-out focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)] ${selected ? 'border border-transparent bg-primary text-on-primary' : 'border border-[var(--task-detail-border)] bg-transparent text-[var(--task-detail-secondary)] hover:border-[var(--task-detail-input-hover)] hover:text-[var(--task-detail-primary)]'}`}>
                      {day.slice(0, 1)}
                    </button>
                  )
                })}
              </div>
            </fieldset>
          ) : null}

          {mode === 'scheduled' && frequency === 'monthly' ? (
            <div className="mt-4 max-w-56">
              <Field label="Day of month">
                <input aria-label="Day of month" type="number" min={1} max={31} value={monthDay} onChange={(event) => setMonthDay(Number(event.target.value))} className={recurringFieldClass} />
                <span className="mt-1.5 block text-[11px] text-[var(--task-detail-muted)]">Short months use their final day.</span>
              </Field>
            </div>
          ) : null}

          <div className="mt-4 flex items-start gap-2 text-sm leading-[1.6] text-[var(--task-detail-primary)]">
            <span className="material-symbols-outlined mt-0.5 text-[18px] text-[var(--task-detail-muted)]" aria-hidden>event_repeat</span>
            <div>
              <p>{summary}</p>
              {preview && preview.past_cycles > 0 ? <p className="mt-1 text-xs text-[var(--task-detail-muted)]">{preview.past_cycles} past cycles ({preview.past_tasks} tasks) will be backfilled — you&apos;ll be asked to confirm.</p> : null}
            </div>
          </div>
        </section>

        <section className="border-t border-[var(--task-detail-divider)] px-5 py-[18px] sm:px-7 sm:py-[22px]">
          <SectionHeading>{mode === 'scheduled' ? 'Priority & checklist' : 'Priority'}</SectionHeading>
          <div className="flex items-center justify-between gap-4 border-b border-[var(--task-detail-divider)] py-2.5">
            <span className="text-[13px] text-[var(--task-detail-muted)]">Urgency</span>
            <PriorityControl label="Urgency" value={urgency} onChange={setUrgency} />
          </div>
          <div className="flex items-center justify-between gap-4 py-2.5">
            <span className="text-[13px] text-[var(--task-detail-muted)]">Importance</span>
            <PriorityControl label="Importance" value={importance} onChange={setImportance} />
          </div>
          {mode === 'scheduled' ? (
            <textarea rows={3} aria-label="Checklist (one title per line)" placeholder="One checklist item per line" value={checklist} onChange={(event) => setChecklist(event.target.value)} className={`${recurringFieldClass} mt-3.5 resize-y leading-[1.7]`} />
          ) : <p className="mt-3.5 text-xs text-[var(--task-detail-muted)]">Checklists are available on scheduled templates only.</p>}
        </section>

        <footer className="flex items-center justify-end gap-2.5 border-t border-[var(--task-detail-divider)] px-5 py-[18px] sm:px-7">
          <button type="button" className="border-0 bg-transparent px-2 py-[9px] text-[13px] text-[var(--task-detail-secondary)] transition-colors duration-120 ease-out hover:text-[var(--task-detail-primary)] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]" onClick={requestClose}>Cancel</button>
          <button type="submit" disabled={saving || !title.trim()} className="rounded-[10px] bg-primary px-5 py-[9px] text-[13px] font-medium text-on-primary transition-colors duration-120 ease-out hover:bg-primary-dim focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-primary">{saving ? 'Saving…' : template ? 'Save changes' : 'Create recurrence'}</button>
        </footer>
      </form>
    </div>
  )
}

function SectionHeading({ children }: { children: React.ReactNode }) {
  return <h3 className="mb-3.5 font-label text-[11px] uppercase tracking-[0.18em] text-[var(--task-detail-muted)]">{children}</h3>
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label><span className="mb-1.5 block text-xs text-[var(--task-detail-muted)]">{label}</span>{children}</label>
}

function Select({ label, ariaLabel, value, unset = false, onChange, children }: { label: string; ariaLabel?: string; value: string; unset?: boolean; onChange: (value: string) => void; children: React.ReactNode }) {
  return (
    <Field label={label}>
      <select aria-label={ariaLabel ?? label} value={value} onChange={(event) => onChange(event.target.value)} className={`task-detail-select ${recurringFieldClass} appearance-none pr-9 ${unset ? 'text-[var(--task-detail-secondary)]' : ''}`}>{children}</select>
    </Field>
  )
}
