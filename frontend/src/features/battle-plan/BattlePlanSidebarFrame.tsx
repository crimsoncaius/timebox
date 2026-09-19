import { useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type Project, type ProjectWrite } from '../../lib/api'
import { BattlePlanSidebar } from './BattlePlanSidebar'
import { ProjectEditor } from './ProjectEditor'
import { persistBattlePlanScope, projectTaskCount, type BattlePlanScope } from './battlePlanState'

/** Keeps Battle Plan's lists-and-projects sidebar beside Task Types, which lives under Battle Plan. */
export function BattlePlanSidebarFrame({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [projects, setProjects] = useState<Project[]>([])
  const [mobileSidebar, setMobileSidebar] = useState(false)
  const [projectEditor, setProjectEditor] = useState<Project | null | undefined>(undefined)
  const [projectEditorCount, setProjectEditorCount] = useState(0)

  useEffect(() => {
    let live = true
    api.listProjects().then((rows) => { if (live) setProjects(rows) }).catch(() => {})
    return () => { live = false }
  }, [])

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
    <>
      <div className="mb-6 flex items-center justify-between lg:hidden">
        <button type="button" className="rounded-xl bg-surface-container-low px-3 py-2 text-sm dark:bg-dark-surface-container" onClick={() => setMobileSidebar(true)}>Lists & projects</button>
      </div>
      <div className="flex min-h-[calc(100vh-9rem)] gap-6">
        <BattlePlanSidebar
          open={mobileSidebar}
          collection="active"
          scope="all"
          taskTypes
          projects={projects}
          onReorderProjects={async (ids) => { setProjects(await api.reorderProjects(ids)) }}
          onClose={() => setMobileSidebar(false)}
          onScope={openBattlePlanScope}
          onCollection={(collection) => navigate(`/battle-plan?collection=${collection}`)}
          onNewProject={() => { setProjectEditorCount(0); setProjectEditor(null) }}
          onEditProject={(project) => void openProject(project)}
        />
        <section className="min-w-0 flex-1">{children}</section>
      </div>
      {projectEditor !== undefined ? (
        <ProjectEditor
          project={projectEditor}
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
    </>
  )
}
