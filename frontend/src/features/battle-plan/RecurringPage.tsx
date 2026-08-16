import { useCallback, useEffect, useMemo, useState } from 'react'
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

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const inputClass = 'w-full rounded-xl border border-outline-variant/20 bg-surface-container-lowest px-3 py-2.5 text-sm outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/15 dark:border-dark-outline-variant dark:bg-dark-surface-container-lowest'
const buttonClass = 'rounded-xl px-3.5 py-2 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary/30'

function todayIso() {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10)
}

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
  const [creatingMode, setCreatingMode] = useState<RecurrenceMode | null>(null)
  const [editing, setEditing] = useState<RecurringTemplate | null>(null)
  const [mobileSidebar, setMobileSidebar] = useState(false)
  const [projectEditor, setProjectEditor] = useState<Project | null | undefined>(undefined)
  const [projectEditorCount, setProjectEditorCount] = useState(0)
  const [timezone, setTimezone] = useState('UTC')
  const selectedId = Number(searchParams.get('recurring')) || null
  const selected = templates.find((template) => template.id === selectedId) ?? null

  const load = useCallback(async (nextStatus: RecurrenceStatus = status) => {
    setLoading(true)
    setError(null)
    try {
      const [rows, projectRows, typeRows] = await Promise.all([
        api.listRecurringTemplates(nextStatus), api.listProjects(), api.listTaskTypes(),
      ])
      setTemplates(rows)
      setProjects(projectRows)
      setTaskTypes(typeRows)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Failed to load recurring templates')
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => { void load(status) }, [load, status])

  useEffect(() => {
    let active = true
    void api.health().then((health) => { if (active) setTimezone(health.timezone) }).catch(() => undefined)
    return () => { active = false }
  }, [])

  const lifecycle = async (template: RecurringTemplate, action: 'pause' | 'resume' | 'end' | 'delete') => {
    setError(null)
    try {
      if (action === 'pause') await api.pauseRecurringTemplate(template.id)
      if (action === 'resume') await api.resumeRecurringTemplate(template.id)
      if (action === 'end') {
        if (!window.confirm(`End “${template.title}”? Future pristine tasks will be removed.`)) return
        await api.endRecurringTemplate(template.id)
      }
      if (action === 'delete') {
        if (!window.confirm(`Permanently delete “${template.title}”? Generated tasks will be kept as ordinary tasks.`)) return
        await api.deleteRecurringTemplate(template.id)
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
          <div className="flex flex-wrap gap-2">
            <button type="button" className={`${buttonClass} bg-surface-container-low hover:bg-surface-container-high dark:bg-dark-surface-container`} onClick={() => setCreatingMode('scheduled')}>
              On a schedule
            </button>
            <button type="button" className={`${buttonClass} bg-primary text-on-primary hover:bg-primary-dim`} onClick={() => setCreatingMode('quota')}>
              Times per period
            </button>
          </div>
          </header>

        <div className="mt-8 flex gap-1 rounded-xl bg-surface-container-low p-1 sm:w-fit dark:bg-dark-surface-container-low">
          {(['active', 'paused', 'ended'] as RecurrenceStatus[]).map((value) => (
            <button key={value} type="button" onClick={() => { setStatus(value); setSearchParams({ view: 'recurring' }) }} className={`${buttonClass} capitalize ${status === value ? 'bg-surface-container-lowest shadow-sm dark:bg-dark-surface-container-high' : 'text-on-surface-variant'}`}>
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
      {creatingMode || editing ? (
        <TemplateForm
          mode={editing?.mode ?? creatingMode!}
          template={editing}
          projects={projects}
          taskTypes={taskTypes}
          onClose={() => { setCreatingMode(null); setEditing(null) }}
          onSaved={async (saved) => {
            setCreatingMode(null)
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
  onLifecycle: (action: 'pause' | 'resume' | 'end' | 'delete') => void
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
        {template.status === 'ended' ? <button type="button" className={`${buttonClass} px-2.5 py-1.5 text-xs text-error hover:bg-error-container/10`} onClick={() => onLifecycle('delete')}>Delete</button> : null}
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

function TemplateForm({ mode, template, projects, taskTypes, onClose, onSaved }: {
  mode: RecurrenceMode
  template: RecurringTemplate | null
  projects: Project[]
  taskTypes: TaskType[]
  onClose: () => void
  onSaved: (template: RecurringTemplate) => Promise<void>
}) {
  const [title, setTitle] = useState(template?.title ?? '')
  const [description, setDescription] = useState(template?.description ?? '')
  const [projectId, setProjectId] = useState(template?.project_id ? String(template.project_id) : '')
  const [taskTypeId, setTaskTypeId] = useState(template?.task_type_id ? String(template.task_type_id) : '')
  const [urgency, setUrgency] = useState<PriorityLevel | ''>(template?.urgency ?? '')
  const [importance, setImportance] = useState<PriorityLevel | ''>(template?.importance ?? '')
  const [frequency, setFrequency] = useState<RecurrenceFrequency>(template?.frequency ?? 'daily')
  const [interval, setInterval] = useState(template?.interval ?? 1)
  const [weekdays, setWeekdays] = useState<number[]>(template?.weekdays ?? [new Date().getDay() === 0 ? 6 : new Date().getDay() - 1])
  const [monthDay, setMonthDay] = useState(template?.month_day ?? new Date().getDate())
  const [quotaCount, setQuotaCount] = useState(template?.quota_count ?? 3)
  const [startDate, setStartDate] = useState(template?.start_date ?? todayIso())
  const [ending, setEnding] = useState<'never' | 'date' | 'cycles'>(template?.end_date ? 'date' : template?.cycle_limit ? 'cycles' : 'never')
  const [endDate, setEndDate] = useState(template?.end_date ?? '')
  const [cycleLimit, setCycleLimit] = useState(template?.cycle_limit ?? 10)
  const [checklist, setChecklist] = useState(template?.checklist_items.map((item) => item.title).join('\n') ?? '')
  const [preview, setPreview] = useState<RecurrencePreview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

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
        urgency: urgency || null, importance: importance || null,
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
    <div className="fixed inset-0 z-100 flex items-center justify-center bg-black/35 p-3" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label={template ? `Edit ${template.title}` : 'New recurring task'} onSubmit={(event) => void submit(event)} className="max-h-[94vh] w-full max-w-2xl overflow-y-auto rounded-3xl bg-surface p-5 shadow-2xl sm:p-7 dark:bg-dark-background">
        <div className="flex items-start justify-between gap-4"><div><p className="font-label text-xs uppercase tracking-[0.16em] text-on-surface-variant">{template ? 'Edit recurring template' : mode === 'scheduled' ? 'On a schedule' : 'Times per period'}</p><h2 className="mt-2 font-headline text-2xl font-light">{template ? template.title : 'New recurring task'}</h2></div><button type="button" aria-label="Close recurring form" onClick={onClose} className="rounded-full p-2 hover:bg-surface-container-low">×</button></div>
        {error ? <div role="alert" className="mt-5 rounded-xl bg-error-container/20 px-4 py-3 text-sm text-on-error-container">{error}</div> : null}
        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <label className="sm:col-span-2"><span className="mb-1.5 block text-xs text-on-surface-variant">Title</span><input autoFocus required value={title} onChange={(event) => setTitle(event.target.value)} className={inputClass} /></label>
          <label className="sm:col-span-2"><span className="mb-1.5 block text-xs text-on-surface-variant">Description</span><textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={2} className={inputClass} /></label>
          <Select label="Location" value={projectId} onChange={setProjectId}><option value="">Admin</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</Select>
          <Select label="Task type" value={taskTypeId} onChange={setTaskTypeId}><option value="">Unset</option>{taskTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</Select>
          <Select label={mode === 'scheduled' ? 'Repeats' : 'Period'} value={frequency} onChange={(value) => setFrequency(value as RecurrenceFrequency)}><option value="daily">{mode === 'scheduled' ? 'Daily' : 'Calendar day'}</option><option value="weekly">{mode === 'scheduled' ? 'Weekly' : 'Calendar week'}</option><option value="monthly">{mode === 'scheduled' ? 'Monthly' : 'Calendar month'}</option></Select>
          {mode === 'scheduled' ? <label><span className="mb-1.5 block text-xs text-on-surface-variant">Every</span><div className="flex items-center gap-2"><input type="number" min={1} value={interval} onChange={(event) => setInterval(Number(event.target.value))} className={inputClass} /><span className="text-sm text-on-surface-variant">{{ daily: 'days', weekly: 'weeks', monthly: 'months' }[frequency]}</span></div></label> : <label><span className="mb-1.5 block text-xs text-on-surface-variant">Times per period</span><input type="number" min={1} max={100} value={quotaCount} onChange={(event) => setQuotaCount(Number(event.target.value))} className={inputClass} /></label>}
          {mode === 'scheduled' && frequency === 'weekly' ? <fieldset className="sm:col-span-2"><legend className="mb-2 text-xs text-on-surface-variant">Weekdays</legend><div className="flex flex-wrap gap-2">{WEEKDAYS.map((day, index) => <button key={day} type="button" aria-label={day} aria-pressed={weekdays.includes(index)} onClick={() => setWeekdays((current) => current.includes(index) ? current.filter((value) => value !== index) : [...current, index].sort())} className={`${buttonClass} size-10 p-0 text-xs ${weekdays.includes(index) ? 'bg-primary text-on-primary' : 'bg-surface-container-low text-on-surface-variant'}`}>{day.slice(0, 1)}</button>)}</div></fieldset> : null}
          {mode === 'scheduled' && frequency === 'monthly' ? <label><span className="mb-1.5 block text-xs text-on-surface-variant">Day of month</span><input type="number" min={1} max={31} value={monthDay} onChange={(event) => setMonthDay(Number(event.target.value))} className={inputClass} /><span className="mt-1 block text-[11px] text-on-surface-variant">Short months use their final day.</span></label> : null}
          <label><span className="mb-1.5 block text-xs text-on-surface-variant">Start date</span><input required type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className={inputClass} /></label>
          <Select label="Ending" value={ending} onChange={(value) => setEnding(value as typeof ending)}><option value="never">Never</option><option value="date">On date</option><option value="cycles">After cycles</option></Select>
          {ending === 'date' ? <label><span className="mb-1.5 block text-xs text-on-surface-variant">Inclusive end date</span><input required type="date" min={startDate} value={endDate} onChange={(event) => setEndDate(event.target.value)} className={inputClass} /></label> : null}
          {ending === 'cycles' ? <label><span className="mb-1.5 block text-xs text-on-surface-variant">Cycle limit</span><input type="number" min={1} value={cycleLimit} onChange={(event) => setCycleLimit(Number(event.target.value))} className={inputClass} /></label> : null}
          <Select label="Urgency" value={urgency} onChange={(value) => setUrgency(value as PriorityLevel | '')}><option value="">Unset</option><option value="low">Low</option><option value="medium">Medium</option><option value="high">High</option></Select>
          <Select label="Importance" value={importance} onChange={(value) => setImportance(value as PriorityLevel | '')}><option value="">Unset</option><option value="low">Low</option><option value="medium">Medium</option><option value="high">High</option></Select>
          {mode === 'scheduled' ? <label className="sm:col-span-2"><span className="mb-1.5 block text-xs text-on-surface-variant">Checklist (one title per line)</span><textarea value={checklist} onChange={(event) => setChecklist(event.target.value)} rows={3} className={inputClass} /></label> : null}
        </div>
        {preview ? <div className="mt-5 rounded-2xl bg-surface-container-low p-4 text-sm dark:bg-dark-surface-container-low"><p className="font-medium">Preview</p><p className="mt-1 text-xs text-on-surface-variant">{preview.past_cycles ? `${preview.past_cycles} past cycles will require confirmation. ` : ''}Next: {preview.upcoming.map((window) => displayWindow(window.start, window.end)).join(', ') || 'none'}</p></div> : null}
        <div className="mt-6 flex justify-end gap-2"><button type="button" className={`${buttonClass} text-on-surface-variant hover:bg-surface-container-low`} onClick={onClose}>Cancel</button><button type="submit" disabled={saving || !title.trim()} className={`${buttonClass} bg-primary text-on-primary disabled:opacity-50`}>{saving ? 'Saving…' : template ? 'Save changes' : 'Create recurrence'}</button></div>
      </form>
    </div>
  )
}

function Select({ label, value, onChange, children }: { label: string; value: string; onChange: (value: string) => void; children: React.ReactNode }) {
  return <label><span className="mb-1.5 block text-xs text-on-surface-variant">{label}</span><select aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} className={inputClass}>{children}</select></label>
}
