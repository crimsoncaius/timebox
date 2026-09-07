import { useEffect, useRef, useState } from 'react'
import { ApiHttpError, type BattleTask, type Project } from '../../lib/api'

export function MoveProjectDialog({ task, projects, onMove, onClose }: {
  task: BattleTask
  projects: Project[]
  onMove: (projectId: number | null) => Promise<void>
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [destination, setDestination] = useState(task.project_id?.toString() ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => { if (dialog.current && !dialog.current.open) dialog.current.showModal() }, [])
  return <dialog ref={dialog} aria-labelledby="move-project-title" onCancel={(event) => {
    event.preventDefault()
    if (!saving) onClose()
  }} className="m-auto w-full max-w-md rounded-2xl bg-surface-container-lowest p-6 text-on-surface shadow-xl backdrop:bg-black/40 dark:bg-dark-surface-container dark:text-dark-on-surface">
    <form onSubmit={async (event) => {
      event.preventDefault()
      if (saving) return
      setSaving(true)
      setError(null)
      try { await onMove(destination ? Number(destination) : null); onClose() }
      catch (cause) {
        setError(cause instanceof ApiHttpError ? cause.detailMessage : 'Could not move task. Please try again.')
        setSaving(false)
      }
    }}>
      <h2 id="move-project-title" className="text-lg">Move to project</h2>
      <p className="my-3 break-words text-sm">{task.title}</p>
      <label className="block text-sm">Destination
        <select autoFocus value={destination} disabled={saving} onChange={(event) => setDestination(event.target.value)} className="mt-2 w-full rounded-lg bg-surface-container-low p-3 dark:bg-dark-surface-container-high">
          <option value="">Admin</option>
          {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
        </select>
      </label>
      {error && <p role="alert" className="mt-3 text-sm text-error">{error}</p>}
      <div className="mt-5 flex justify-end gap-3">
        <button type="button" disabled={saving} onClick={onClose} className="rounded-lg px-4 py-2">Cancel</button>
        <button type="submit" disabled={saving || destination === (task.project_id?.toString() ?? '')} className="rounded-lg bg-primary px-4 py-2 text-on-primary disabled:opacity-50">{saving ? 'Moving…' : 'Move'}</button>
      </div>
    </form>
  </dialog>
}
