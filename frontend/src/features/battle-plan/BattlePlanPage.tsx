import { DragDropProvider, useDroppable, type DragEndEvent } from '@dnd-kit/react'
import { isSortable } from '@dnd-kit/react/sortable'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import {
  deadlineRank,
  PRIORITY_LEVELS,
  priorityRank,
  STATUS_LABELS,
  TASK_STATUSES,
} from '../../lib/battlePlan'
import {
  ApiHttpError,
  api,
  type BattleTask,
  type BattleTaskWrite,
  type PriorityLevel,
  type Project,
  type ProjectWrite,
  type TaskCollection,
  type TaskStatus,
  type TaskType,
} from '../../lib/api'
import { BattlePlanCard } from './BattlePlanCard'
import { useAppClock } from '../../lib/useAppClock'
import { BattlePlanSidebar } from './BattlePlanSidebar'
import { BATTLE_PLAN_STORAGE_KEY, type BattlePlanScope } from './battlePlanState'
import { ProjectEditor } from './ProjectEditor'
import { TaskComposer } from './TaskComposer'
import { TaskDetailPanel } from './TaskDetailPanel'

type Scope = BattlePlanScope
type SortMode = 'manual' | 'deadline' | 'urgency' | 'importance'
type NullableFilter = PriorityLevel | 'unset'

type Preferences = {
  version: 1
  scope: Scope
  sort: SortMode
  hideCompleted: boolean
  urgency: NullableFilter[]
  importance: NullableFilter[]
  taskTypes: string[]
}

const DEFAULT_PREFERENCES: Preferences = {
  version: 1,
  scope: 'all',
  sort: 'manual',
  hideCompleted: false,
  urgency: [],
  importance: [],
  taskTypes: [],
}

function readPreferences(): Preferences {
  try {
    const parsed = JSON.parse(localStorage.getItem(BATTLE_PLAN_STORAGE_KEY) ?? '') as Preferences
    return parsed?.version === 1 ? { ...DEFAULT_PREFERENCES, ...parsed } : DEFAULT_PREFERENCES
  } catch {
    return DEFAULT_PREFERENCES
  }
}

function errorMessage(error: unknown) {
  if (error instanceof ApiHttpError) return error.detailMessage
  if (error instanceof Error) return error.message
  return 'Something went wrong'
}

function findTask(tasks: BattleTask[], id: number | null) {
  if (id == null) return null
  for (const task of tasks) {
    if (task.id === id) return task
    const subtask = task.subtasks.find((item) => item.id === id)
    if (subtask) return subtask
  }
  return null
}

function taskCount(tasks: BattleTask[], projectId: number) {
  return tasks.reduce(
    (count, task) => count + (task.project_id === projectId ? 1 + task.subtasks.length : 0),
    0,
  )
}

export function BattlePlanPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedCollectionParam = searchParams.get('collection')
  const requestedCollection: TaskCollection = requestedCollectionParam === 'archived' || requestedCollectionParam === 'trash'
    ? requestedCollectionParam
    : 'active'
  const requestedTaskIdValue = Number(searchParams.get('task'))
  const requestedTaskId = Number.isFinite(requestedTaskIdValue) && requestedTaskIdValue > 0
    ? requestedTaskIdValue
    : null
  const [preferences, setPreferences] = useState<Preferences>(readPreferences)
  const collection = requestedCollection
  const [loadedCollection, setLoadedCollection] = useState<TaskCollection | null>(null)
  const [tasks, setTasks] = useState<BattleTask[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [taskTypes, setTaskTypes] = useState<TaskType[]>([])
  const [timezone, setTimezone] = useState('UTC')
  const [serverNowIso, setServerNowIso] = useState('1970-01-01T00:00:00Z')
  const [error, setError] = useState<string | null>(null)
  const selectedTaskId = requestedTaskId
  const [projectEditor, setProjectEditor] = useState<Project | null | undefined>(undefined)
  const [projectEditorCount, setProjectEditorCount] = useState(0)
  const [mobileSidebar, setMobileSidebar] = useState(false)
  const [undoTaskId, setUndoTaskId] = useState<number | null>(null)
  const screenNowIso = useAppClock(serverNowIso, timezone)

  const setPrefs = useCallback((change: Partial<Preferences>) => {
    setPreferences((current) => {
      const next = { ...current, ...change }
      localStorage.setItem(BATTLE_PLAN_STORAGE_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  const loadActive = useCallback(async () => {
    const result = await api.listBattleTasks('active')
    setTasks(result.items)
    setTimezone(result.timezone)
    setServerNowIso(result.server_now_iso)
  }, [])

  const loadCollection = useCallback(async (state: TaskCollection) => {
    const result = await api.listBattleTasks(state)
    setTasks(result.items)
    setTimezone(result.timezone)
    setServerNowIso(result.server_now_iso)
  }, [])

  useEffect(() => {
    let active = true
    Promise.all([api.listBattleTasks(collection), api.listProjects(), api.listTaskTypes()])
      .then(([taskResult, projectRows, typeRows]) => {
        if (!active) return
        setTasks(taskResult.items)
        setTimezone(taskResult.timezone)
        setServerNowIso(taskResult.server_now_iso)
        setProjects(projectRows)
        setTaskTypes(typeRows)
        setError(null)
        setLoadedCollection(collection)
        if (preferences.scope.startsWith('project:')) {
          const id = Number(preferences.scope.slice(8))
          if (!projectRows.some((project) => project.id === id)) setPrefs({ scope: 'all' })
        }
      })
      .catch((cause) => {
        if (!active) return
        setTasks([])
        setError(errorMessage(cause))
        setLoadedCollection(collection)
      })
    return () => { active = false }
  }, [collection, preferences.scope, setPrefs])

  const switchCollection = (next: TaskCollection) => {
    setSearchParams(next === 'active' ? {} : { collection: next })
    setError(null)
  }

  const scopeFiltered = useMemo(() => tasks.filter((task) => {
    if (preferences.scope === 'admin') return task.project_id == null
    if (preferences.scope.startsWith('project:')) return task.project_id === Number(preferences.scope.slice(8))
    return true
  }), [preferences.scope, tasks])

  const visibleTasks = useMemo(() => {
    const matchesNullable = (selected: NullableFilter[], value: PriorityLevel | null) =>
      selected.length === 0 || selected.includes(value ?? 'unset')
    const rows = scopeFiltered.filter((task) => {
      if (preferences.hideCompleted && task.status === 'completed') return false
      if (!matchesNullable(preferences.urgency, task.urgency)) return false
      if (!matchesNullable(preferences.importance, task.importance)) return false
      if (preferences.taskTypes.length > 0) {
        const key = task.task_type_id == null ? 'unset' : String(task.task_type_id)
        if (!preferences.taskTypes.includes(key)) return false
      }
      return true
    })
    return [...rows].sort((left, right) => {
      if (preferences.sort === 'deadline') return deadlineRank(left) - deadlineRank(right) || left.position - right.position
      if (preferences.sort === 'urgency') return priorityRank(right.urgency) - priorityRank(left.urgency) || left.position - right.position
      if (preferences.sort === 'importance') return priorityRank(right.importance) - priorityRank(left.importance) || left.position - right.position
      return left.position - right.position
    })
  }, [preferences, scopeFiltered])

  const columns = useMemo(() => Object.fromEntries(
    TASK_STATUSES.map((status) => [status, visibleTasks.filter((task) => task.status === status)]),
  ) as Record<TaskStatus, BattleTask[]>, [visibleTasks])

  const createTask = async (body: BattleTaskWrite) => {
    setError(null)
    try {
      await api.createBattleTask(body)
      await loadActive()
    } catch (cause) {
      setError(errorMessage(cause))
      throw cause
    }
  }

  const addSubtask = async (parentId: number, title: string) => {
    setError(null)
    try {
      await api.createBattleTask({ parent_id: parentId, title })
      await loadActive()
    } catch (cause) {
      setError(errorMessage(cause))
      throw cause
    }
  }

  const patchTask = async (id: number, patch: Partial<BattleTaskWrite>) => {
    setError(null)
    try {
      await api.patchBattleTask(id, patch)
      await loadActive()
    } catch (cause) {
      setError(errorMessage(cause))
      throw cause
    }
  }

  const handleDragEnd = async (event: DragEndEvent) => {
    const { source, target } = event.operation
    if (event.canceled || !isSortable(source) || !target) return
    const movingId = Number(source.id)
    const moving = tasks.find((task) => task.id === movingId)
    if (!moving) return
    let targetStatus: TaskStatus
    let targetIndex: number
    if (isSortable(target)) {
      targetStatus = String(target.group) as TaskStatus
      targetIndex = String(source.group) === targetStatus ? source.index : target.index
    } else if (String(target.id).startsWith('column:')) {
      targetStatus = String(target.id).slice(7) as TaskStatus
      targetIndex = columns[targetStatus].filter((task) => task.id !== movingId).length
    } else return
    if (!TASK_STATUSES.includes(targetStatus)) return
    if (preferences.sort !== 'manual' && targetStatus === moving.status) return

    const previous = tasks
    const groups = Object.fromEntries(TASK_STATUSES.map((status) => [
      status,
      tasks.filter((task) => task.status === status && task.id !== movingId).sort((a, b) => a.position - b.position),
    ])) as Record<TaskStatus, BattleTask[]>
    const visibleTargetIds = columns[targetStatus].filter((task) => task.id !== movingId).map((task) => task.id)
    const beforeId = visibleTargetIds[targetIndex]
    const afterId = targetIndex > 0 ? visibleTargetIds[targetIndex - 1] : undefined
    let insertAt = groups[targetStatus].length
    if (beforeId != null) insertAt = groups[targetStatus].findIndex((task) => task.id === beforeId)
    else if (afterId != null) insertAt = groups[targetStatus].findIndex((task) => task.id === afterId) + 1
    if (insertAt < 0) insertAt = groups[targetStatus].length
    groups[targetStatus].splice(insertAt, 0, { ...moving, status: targetStatus })
    const next = TASK_STATUSES.flatMap((status) => groups[status].map((task, position) => ({ ...task, status, position })))
    setTasks(next)
    try {
      await api.reorderBattleTasks(next.map((task) => ({ task_id: task.id, status: task.status, position: task.position })))
    } catch (cause) {
      setTasks(previous)
      setError(errorMessage(cause))
    }
  }

  const openTask = (id: number) => {
    setSearchParams({ task: String(id) })
  }

  const closeTask = () => {
    setSearchParams({})
  }

  const openProject = async (project: Project) => {
    setProjectEditor(project)
    try {
      const [active, archived, trash] = await Promise.all([
        api.listBattleTasks('active'), api.listBattleTasks('archived'), api.listBattleTasks('trash'),
      ])
      setProjectEditorCount(taskCount([...active.items, ...archived.items, ...trash.items], project.id))
    } catch { setProjectEditorCount(taskCount(tasks, project.id)) }
  }

  const selectedTask = findTask(tasks, selectedTaskId)
  const activeProject = preferences.scope.startsWith('project:')
    ? projects.find((project) => project.id === Number(preferences.scope.slice(8))) ?? null
    : null

  return (
    <Layout mainClassName="w-full px-4 py-6 sm:px-8 lg:px-10">
      <div className="mb-6 flex items-center justify-between lg:hidden">
        <button type="button" className="rounded-xl bg-surface-container-low px-3 py-2 text-sm dark:bg-dark-surface-container" onClick={() => setMobileSidebar(true)}>Lists & projects</button>
      </div>
      <div className="flex min-h-[calc(100vh-9rem)] gap-6">
        <BattlePlanSidebar
          open={mobileSidebar}
          collection={collection}
          scope={preferences.scope}
          projects={projects}
          onClose={() => setMobileSidebar(false)}
          onScope={(scope) => { setPrefs({ scope }); void switchCollection('active'); setMobileSidebar(false) }}
          onCollection={(state) => { void switchCollection(state); setMobileSidebar(false) }}
          onNewProject={() => { setProjectEditorCount(0); setProjectEditor(null) }}
          onEditProject={(project) => void openProject(project)}
        />

        <section className="min-w-0 flex-1">
          <header className="mb-7 flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
            <div>
              <p className="font-label text-xs uppercase tracking-[0.18em] text-on-surface-variant">Battle Plan</p>
              <h1 className="mt-2 font-headline text-[2.5rem] font-extralight leading-none tracking-tighter">
                {collection === 'archived' ? 'Archive' : collection === 'trash' ? 'Trash' : activeProject?.name ?? (preferences.scope === 'admin' ? 'Admin' : 'All Tasks')}
              </h1>
            </div>
            {collection === 'active' ? (
              <div className="flex flex-wrap items-center gap-2">
                <select aria-label="Sort tasks" value={preferences.sort} onChange={(event) => setPrefs({ sort: event.target.value as SortMode })} className="rounded-xl bg-surface-container-low px-3 py-2 text-sm outline-none transition-shadow focus-visible:ring-1 focus-visible:ring-primary/25 dark:bg-dark-surface-container">
                  <option value="manual">Manual order</option>
                  <option value="deadline">Deadline</option>
                  <option value="urgency">Urgency</option>
                  <option value="importance">Importance</option>
                </select>
                <label className="flex items-center gap-2 rounded-xl bg-surface-container-low px-3 py-2 text-sm dark:bg-dark-surface-container">
                  <input type="checkbox" checked={preferences.hideCompleted} onChange={(event) => setPrefs({ hideCompleted: event.target.checked })} /> Hide completed
                </label>
                {columns.completed.length > 0 ? (
                  <button type="button" className="rounded-xl bg-surface-container-low px-3 py-2 text-sm dark:bg-dark-surface-container" onClick={async () => { await api.archiveBattleTasks(columns.completed.map((task) => task.id)); await loadActive() }}>
                    Archive completed ({columns.completed.length})
                  </button>
                ) : null}
              </div>
            ) : null}
          </header>

          {error && loadedCollection === collection ? <div role="alert" className="mb-5 rounded-xl bg-error-container/20 px-4 py-3 text-sm text-on-error-container">{error}</div> : null}
          {loadedCollection !== collection ? <p className="text-on-surface-variant">Loading Battle Plan…</p> : collection === 'active' ? (
            <>
              <TaskFilters preferences={preferences} taskTypes={taskTypes} onChange={setPrefs} />
              <DragDropProvider onDragEnd={(event) => void handleDragEnd(event)}>
                <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                  {TASK_STATUSES.map((status) => (
                    <KanbanColumn
                      key={status}
                      status={status}
                      tasks={columns[status]}
                      projects={projects}
                      taskTypes={taskTypes}
                      scope={preferences.scope}
                      timezone={timezone}
                      serverNowIso={screenNowIso}
                      onCreate={createTask}
                      onOpen={openTask}
                      onAddSubtask={addSubtask}
                      onPatchSubtask={(id, nextStatus) => patchTask(id, { status: nextStatus })}
                      onToggleReady={(id, ready) => patchTask(id, { ready_to_plan: ready })}
                    />
                  ))}
                </div>
              </DragDropProvider>
            </>
          ) : (
            <UtilityTaskList
              tasks={tasks}
              collection={collection}
              serverNowIso={screenNowIso}
              onRestore={async (task) => {
                if (collection === 'archived') await api.unarchiveBattleTask(task.id)
                else await api.restoreBattleTask(task.id)
                await loadCollection(collection)
              }}
              onPermanentDelete={async (task) => {
                if (!window.confirm(`Permanently delete “${task.title}”? This cannot be undone.`)) return
                await api.permanentlyDeleteBattleTask(task.id)
                await loadCollection('trash')
              }}
            />
          )}
        </section>
      </div>

      {selectedTask && collection === 'active' ? (
        <TaskDetailPanel
          key={selectedTask.parent_id ?? selectedTask.id}
          task={selectedTask.parent_id == null ? selectedTask : tasks.find((task) => task.id === selectedTask.parent_id) ?? selectedTask}
          projects={projects}
          taskTypes={taskTypes}
          timezone={timezone}
          serverNowIso={screenNowIso}
          onClose={closeTask}
          onPatch={patchTask}
          onAddSubtask={addSubtask}
          onTrash={async (id) => {
            await api.trashBattleTask(id)
            setUndoTaskId(id)
            const openTaskId = selectedTask.parent_id == null ? selectedTask.id : selectedTask.parent_id
            if (id === openTaskId) closeTask()
            await loadActive()
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
            setPrefs({ scope: 'all' })
            setProjectEditor(undefined)
            await loadActive()
          } : null}
        />
      ) : null}

      {undoTaskId != null ? (
        <div className="fixed bottom-5 left-1/2 z-100 flex -translate-x-1/2 items-center gap-4 rounded-full bg-on-surface px-5 py-3 text-sm text-surface shadow-xl dark:bg-dark-on-surface dark:text-dark-background">
          Moved to Trash
          <button type="button" className="font-medium underline" onClick={async () => { await api.restoreBattleTask(undoTaskId); setUndoTaskId(null); await loadActive() }}>Undo</button>
          <button type="button" aria-label="Dismiss undo" onClick={() => setUndoTaskId(null)}>×</button>
        </div>
      ) : null}
    </Layout>
  )
}

function KanbanColumn({ status, tasks, projects, taskTypes, scope, timezone, serverNowIso, onCreate, onOpen, onAddSubtask, onPatchSubtask, onToggleReady }: {
  status: TaskStatus
  tasks: BattleTask[]
  projects: Project[]
  taskTypes: TaskType[]
  scope: Scope
  timezone: string
  serverNowIso: string
  onCreate: (task: BattleTaskWrite) => Promise<void>
  onOpen: (id: number) => void
  onAddSubtask: (parentId: number, title: string) => Promise<void>
  onPatchSubtask: (id: number, status: TaskStatus) => Promise<void>
  onToggleReady: (id: number, ready: boolean) => Promise<void>
}) {
  const { ref, isDropTarget } = useDroppable({ id: `column:${status}`, accept: 'battle-task' })
  const fixedProjectId = scope === 'all'
    ? undefined
    : scope === 'admin'
      ? null
      : Number(scope.slice(8))
  return (
    <section ref={ref} aria-label={`${STATUS_LABELS[status]} tasks`} className={`min-h-72 rounded-3xl bg-surface-container-low p-3 transition-colors dark:bg-dark-surface-container-low ${isDropTarget ? 'ring-1 ring-primary/30' : ''}`}>
      <div className="flex items-center justify-between px-1.5 pb-3 pt-1">
        <h2 className="font-headline text-sm font-normal tracking-tight">{STATUS_LABELS[status]}</h2>
        <span className="text-[13px] text-outline">{tasks.length}</span>
      </div>
      <TaskComposer
        key={`${scope}:${status}`}
        status={status}
        projects={projects}
        taskTypes={taskTypes}
        fixedProjectId={fixedProjectId}
        timezone={timezone}
        serverNowIso={serverNowIso}
        onCreate={onCreate}
      />
      <div className="space-y-3">
        {tasks.map((task, index) => (
          <BattlePlanCard
            key={task.id}
            task={task}
            index={index}
            column={status}
            timezone={timezone}
            serverNowIso={serverNowIso}
            onOpen={(id = task.id) => onOpen(id)}
            onAddSubtask={onAddSubtask}
            onPatchSubtask={onPatchSubtask}
            onToggleReady={onToggleReady}
          />
        ))}
      </div>
    </section>
  )
}

function TaskFilters({ preferences, taskTypes, onChange }: { preferences: Preferences; taskTypes: TaskType[]; onChange: (change: Partial<Preferences>) => void }) {
  const toggle = <T,>(values: T[], value: T) => values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
  return (
    <div className="flex flex-wrap items-start gap-3 rounded-2xl bg-surface-container-low/70 p-3 dark:bg-dark-surface-container-low/70">
      <FilterGroup label="Urgency" values={[...PRIORITY_LEVELS, 'unset']} selected={preferences.urgency} onToggle={(value) => onChange({ urgency: toggle(preferences.urgency, value as NullableFilter) })} />
      <FilterGroup label="Importance" values={[...PRIORITY_LEVELS, 'unset']} selected={preferences.importance} onToggle={(value) => onChange({ importance: toggle(preferences.importance, value as NullableFilter) })} />
      <details className="relative">
        <summary className="cursor-pointer list-none rounded-full bg-surface-container-lowest px-3 py-2 text-xs dark:bg-dark-surface-container">
          Task types{preferences.taskTypes.length ? ` · ${preferences.taskTypes.length}` : ''}
        </summary>
        <div className="absolute left-0 top-11 z-40 max-h-72 w-64 overflow-y-auto rounded-2xl bg-surface-container-lowest p-3 shadow-xl dark:bg-dark-surface-container-high">
          {[{ id: 'unset', name: 'Unset' }, ...taskTypes.map((taskType) => ({ id: String(taskType.id), name: taskType.name }))].map((item) => (
            <label key={item.id} className="flex items-center gap-2 rounded-lg px-2 py-2 text-sm hover:bg-surface-container-low dark:hover:bg-dark-surface-container">
              <input type="checkbox" checked={preferences.taskTypes.includes(item.id)} onChange={() => onChange({ taskTypes: toggle(preferences.taskTypes, item.id) })} />
              <span className="min-w-0 truncate">{item.name}</span>
            </label>
          ))}
        </div>
      </details>
      {(preferences.urgency.length || preferences.importance.length || preferences.taskTypes.length) ? <button type="button" className="px-2 py-2 text-xs text-on-surface-variant" onClick={() => onChange({ urgency: [], importance: [], taskTypes: [] })}>Clear filters</button> : null}
    </div>
  )
}

function FilterGroup({ label, values, selected, onToggle }: { label: string; values: string[]; selected: string[]; onToggle: (value: string) => void }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="px-1 text-[10px] uppercase tracking-[0.12em] text-on-surface-variant">{label}</span>
      {values.map((value) => <button key={value} type="button" aria-pressed={selected.includes(value)} onClick={() => onToggle(value)} className={`rounded-full px-2.5 py-1.5 text-xs capitalize ${selected.includes(value) ? 'bg-primary text-on-primary' : 'bg-surface-container-lowest dark:bg-dark-surface-container'}`}>{value}</button>)}
    </div>
  )
}

function UtilityTaskList({ tasks, collection, serverNowIso, onRestore, onPermanentDelete }: { tasks: BattleTask[]; collection: TaskCollection; serverNowIso: string; onRestore: (task: BattleTask) => Promise<void>; onPermanentDelete: (task: BattleTask) => Promise<void> }) {
  if (tasks.length === 0) return <p className="rounded-2xl bg-surface-container-low p-6 text-sm text-on-surface-variant dark:bg-dark-surface-container-low">Nothing here.</p>
  return (
    <div className="space-y-3">
      {tasks.map((task) => {
        const remaining = task.deleted_at ? Math.max(0, 30 - Math.floor((new Date(serverNowIso).getTime() - new Date(task.deleted_at).getTime()) / 86_400_000)) : null
        return (
          <article key={task.id} className="flex flex-col gap-3 rounded-2xl bg-surface-container-low p-4 dark:bg-dark-surface-container-low sm:flex-row sm:items-center">
            <div className="min-w-0 flex-1">
              <h2 className="font-headline text-base font-normal">{task.title}</h2>
              <p className="mt-1 text-xs text-on-surface-variant">{task.project?.name ?? 'Admin'}{remaining != null ? ` · ${remaining} days remaining` : ''}</p>
            </div>
            <div className="flex gap-2">
              <button type="button" className="rounded-xl bg-surface-container-lowest px-3 py-2 text-sm dark:bg-dark-surface-container" onClick={() => void onRestore(task)}>Restore</button>
              {collection === 'trash' ? <button type="button" className="rounded-xl px-3 py-2 text-sm text-error" onClick={() => void onPermanentDelete(task)}>Delete permanently</button> : null}
            </div>
          </article>
        )
      })}
    </div>
  )
}
