import { useId, useRef, useState } from 'react'

export function SubtaskTitle({ id, title, disabled, className, onRename }: {
  id: number
  title: string
  disabled: boolean
  className: string
  onRename: (id: number, title: string) => Promise<void>
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(title)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const pending = useRef(false)
  const button = useRef<HTMLButtonElement>(null)
  const messageId = useId()
  const cleanTitle = draft.trim()
  const tooLong = Array.from(cleanTitle).length > 500
  const canSave = !disabled && !saving && !!cleanTitle && !tooLong && cleanTitle !== title

  function close() {
    setEditing(false)
    requestAnimationFrame(() => button.current?.focus())
  }

  async function save() {
    if (!canSave || pending.current) return
    pending.current = true
    setSaving(true)
    setError(null)
    try {
      await onRename(id, cleanTitle)
      close()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not rename subtask. Please retry.')
    } finally {
      pending.current = false
      setSaving(false)
    }
  }

  if (!editing || disabled) return (
    <button ref={button} type="button" disabled={disabled} aria-label={`Rename subtask ${title}`}
      className={`${className} rounded text-left break-words hover:underline focus-visible:outline-none focus-visible:ring-1 disabled:no-underline`}
      onClick={() => { setDraft(title); setError(null); setEditing(true) }}>
      {title}
    </button>
  )

  return (
    <form className="min-w-0 flex-1 space-y-2" aria-label={`Rename subtask ${title}`}
      onSubmit={(event) => { event.preventDefault(); void save() }}
      onKeyDown={(event) => {
        event.stopPropagation()
        if (event.key === 'Escape') { event.preventDefault(); if (!pending.current) close() }
      }}>
      <input autoFocus aria-label="Subtask title" value={draft} disabled={saving}
        aria-invalid={tooLong || !!error} aria-describedby={tooLong || error ? messageId : undefined}
        onFocus={(event) => event.target.select()}
        onChange={(event) => { setDraft(event.target.value); setError(null) }}
        className="w-full min-w-0 rounded-lg border border-outline-variant bg-transparent px-2 py-1 text-sm focus-visible:outline-none focus-visible:ring-1" />
      {tooLong || error ? <p id={messageId} role="alert" className="text-xs text-red-600">{tooLong ? 'Use 500 characters or fewer.' : error}</p> : null}
      <div className="flex gap-2 text-xs">
        <button type="submit" disabled={!canSave} className="rounded-lg bg-primary px-3 py-1.5 text-on-primary disabled:opacity-40">{saving ? 'Saving…' : 'Save subtask'}</button>
        <button type="button" disabled={saving} onClick={close} className="rounded-lg px-3 py-1.5 disabled:opacity-40">Cancel</button>
      </div>
    </form>
  )
}
