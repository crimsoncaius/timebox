import { Link } from 'react-router-dom'
import { useState } from 'react'
import { DragDropProvider, type DragEndEvent } from '@dnd-kit/react'
import { isSortable, useSortable } from '@dnd-kit/react/sortable'
import type { Project, TaskCollection } from '../../lib/api'
import type { BattlePlanScope } from './battlePlanState'

export function BattlePlanSidebar({ open, collection, scope, recurring = false, projects, onClose, onScope, onCollection, onNewProject, onEditProject, onReorderProjects }: {
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
  onReorderProjects: (ids: number[]) => Promise<void>
}) {
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState('')
  const move = async (from: number, to: number) => {
    if (saving || from === to || to < 0 || to >= projects.length) return
    const ids = projects.map((project) => project.id)
    ids.splice(to, 0, ids.splice(from, 1)[0])
    setSaving(true)
    setNotice('Saving project order…')
    try {
      await onReorderProjects(ids)
      setNotice(`${projects[from].name} moved to position ${to + 1} of ${projects.length}.`)
    } catch {
      setNotice('Could not save project order. Refresh and try again.')
    } finally { setSaving(false) }
  }
  const drop = (event: DragEndEvent) => {
    const source = event.operation.source
    if (!event.canceled && isSortable(source)) void move(source.initialIndex, source.index)
  }
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
        <DragDropProvider onDragEnd={drop}>
          <ul aria-label="Projects" aria-busy={saving} className="mt-2 space-y-1">
            {projects.map((project, index) => <ProjectRow key={project.id} project={project} index={index} count={projects.length} saving={saving}
              active={!recurring && collection === 'active' && scope === `project:${project.id}`}
              onSelect={() => onScope(`project:${project.id}`)} onEdit={() => onEditProject(project)} onMove={(to) => void move(index, to)} />)}
          </ul>
        </DragDropProvider>
        <p role="status" className="mt-2 px-3 text-xs text-on-surface-variant">{notice}</p>
        <nav className="mt-10 space-y-1 border-t border-outline-variant/15 pt-5 dark:border-dark-outline-variant/30">
          <button type="button" className={buttonClass(!recurring && collection === 'archived')} onClick={() => onCollection('archived')}>Archive</button>
          <button type="button" className={buttonClass(!recurring && collection === 'trash')} onClick={() => onCollection('trash')}>Trash</button>
        </nav>
      </aside>
    </>
  )
}

function ProjectRow({ project, index, count, active, saving, onSelect, onEdit, onMove }: {
  project: Project; index: number; count: number; active: boolean; saving: boolean
  onSelect: () => void; onEdit: () => void; onMove: (to: number) => void
}) {
  const { ref, handleRef, isDragging } = useSortable({ id: `project:${project.id}`, index, group: 'projects', type: 'project', accept: 'project', disabled: saving })
  const [menu, setMenu] = useState(false)
  return <li ref={ref} className={`group relative rounded-lg ${isDragging ? 'z-10 bg-surface-container-high shadow-lg ring-2 ring-primary' : ''}`}>
    <div className={`flex items-center rounded-lg ${active ? 'bg-surface-container-high dark:bg-dark-surface-container-high' : 'hover:bg-surface-container-low dark:hover:bg-dark-surface-container'}`}>
      <button ref={handleRef} type="button" disabled={saving} aria-label={`Reorder ${project.name}`} title="Drag to reorder, or press Space and use arrow keys" className="touch-none rounded p-1 text-on-surface-variant opacity-40 hover:opacity-100 focus:opacity-100 group-hover:opacity-100">
        <span className="material-symbols-outlined text-[16px]" aria-hidden>drag_indicator</span>
      </button>
      <button type="button" onClick={onSelect} aria-current={active ? 'page' : undefined} className="flex min-w-0 flex-1 items-center gap-2 py-2 text-left text-sm">
        <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden>folder</span><span className="truncate">{project.name}</span>
      </button>
      <button type="button" aria-label={`More actions for ${project.name}`} aria-expanded={menu} onClick={() => setMenu(!menu)} className="rounded p-1 text-on-surface-variant hover:bg-surface-container-high">
        <span className="material-symbols-outlined text-[18px]" aria-hidden>more_horiz</span>
      </button>
    </div>
    {menu && <div className="ml-6 rounded-lg border border-outline-variant/30 bg-surface p-1 dark:bg-dark-surface-container" onKeyDown={(event) => { if (event.key === 'Escape') setMenu(false) }}>
      <button type="button" className="block w-full rounded px-3 py-2 text-left text-sm hover:bg-surface-container-high" onClick={() => { setMenu(false); onEdit() }}>Edit {project.name}</button>
      <button type="button" disabled={saving || index === 0} className="block w-full rounded px-3 py-2 text-left text-sm disabled:opacity-40" onClick={() => onMove(index - 1)}>Move up</button>
      <button type="button" disabled={saving || index === count - 1} className="block w-full rounded px-3 py-2 text-left text-sm disabled:opacity-40" onClick={() => onMove(index + 1)}>Move down</button>
    </div>}
  </li>
}
