import { Link } from 'react-router-dom'
import type { Project, TaskCollection } from '../../lib/api'
import type { BattlePlanScope } from './battlePlanState'

export function BattlePlanSidebar({ open, collection, scope, recurring = false, projects, onClose, onScope, onCollection, onNewProject, onEditProject }: {
  open: boolean
  collection: TaskCollection
  scope: BattlePlanScope
  recurring?: boolean
  projects: Project[]
  onClose: () => void
  onScope: (scope: BattlePlanScope) => void
  onCollection: (state: TaskCollection) => void
  onNewProject: () => void
  onEditProject: (project: Project) => void
}) {
  const buttonClass = (active: boolean) => `flex w-full items-center rounded-xl px-3 py-2 text-left text-sm transition ${active ? 'bg-surface-container-high text-on-surface dark:bg-dark-surface-container-high' : 'text-on-surface-variant hover:bg-surface-container-low dark:hover:bg-dark-surface-container'}`
  return (
    <>
      {open ? <button type="button" className="fixed inset-0 z-60 bg-black/30 lg:hidden" aria-label="Close project sidebar" onClick={onClose} /> : null}
      <aside aria-label="Battle Plan lists and projects" className={`${open ? 'translate-x-0' : '-translate-x-full'} fixed bottom-0 left-0 top-0 z-70 w-72 overflow-y-auto bg-surface p-5 shadow-xl transition-transform dark:bg-dark-background lg:static lg:z-auto lg:w-56 lg:shrink-0 lg:translate-x-0 lg:bg-transparent lg:p-0 lg:shadow-none`}>
        <div className="mb-5 flex items-center justify-between lg:hidden"><span className="font-headline">Battle Plan</span><button type="button" aria-label="Close project sidebar" onClick={onClose}>×</button></div>
        <nav className="space-y-1">
          <button type="button" className={buttonClass(!recurring && collection === 'active' && scope === 'all')} onClick={() => onScope('all')}>All Tasks</button>
          <button type="button" className={buttonClass(!recurring && collection === 'active' && scope === 'admin')} onClick={() => onScope('admin')}>Admin</button>
          <Link to="/battle-plan?view=recurring" className={buttonClass(recurring)} onClick={onClose}>Recurring</Link>
        </nav>
        <div className="mt-8 flex items-center justify-between px-3">
          <span className="font-label text-[10px] uppercase tracking-[0.16em] text-on-surface-variant">Projects</span>
          <button type="button" aria-label="New project" className="text-on-surface-variant" onClick={onNewProject}>+</button>
        </div>
        <div className="mt-2 space-y-1">
          {projects.map((project) => (
            <div key={project.id} className="group flex items-center gap-1">
              <button type="button" className={`${buttonClass(!recurring && collection === 'active' && scope === `project:${project.id}`)} min-w-0 flex-1 truncate`} onClick={() => onScope(`project:${project.id}`)}>{project.name}</button>
              <button type="button" aria-label={`Edit ${project.name}`} className="rounded-full p-1 text-on-surface-variant opacity-50 hover:bg-surface-container-low group-hover:opacity-100" onClick={() => onEditProject(project)}>
                <span className="material-symbols-outlined text-[17px]" aria-hidden>edit</span>
              </button>
            </div>
          ))}
        </div>
        <nav className="mt-10 space-y-1 border-t border-outline-variant/15 pt-5 dark:border-dark-outline-variant/30">
          <button type="button" className={buttonClass(!recurring && collection === 'archived')} onClick={() => onCollection('archived')}>Archive</button>
          <button type="button" className={buttonClass(!recurring && collection === 'trash')} onClick={() => onCollection('trash')}>Trash</button>
        </nav>
      </aside>
    </>
  )
}
