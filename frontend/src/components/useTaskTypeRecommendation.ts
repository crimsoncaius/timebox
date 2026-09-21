import { useEffect, useRef, useState } from 'react'
import { api, type TaskType } from '../lib/api'

/** Scoped to one editor instance. Manual choices, including Unset, win permanently. */
export function useTaskTypeRecommendation(name: string | undefined, types: TaskType[], selectedId: number | null, enabled = true) {
  const [initialName] = useState(name)
  const [edited, setEdited] = useState(false)
  const manual = useRef(false)
  const [chosen, setChosen] = useState(false)
  const [dismissedName, setDismissedName] = useState<string | null>(null)
  const [result, setResult] = useState<{ name: string; catalog: string; id: number } | null>(null)
  const catalog = JSON.stringify(types.map(t => [t.id, t.name]).sort((a, b) => Number(a[0]) - Number(b[0])))
  const classified = !!selectedId && types.find(t => t.id === selectedId)?.name !== 'unspecified'
  const [seenName, setSeenName] = useState(name)
  if (seenName !== name) {
    setSeenName(name)
    if (name !== initialName) setEdited(true)
    setDismissedName(null)
    setResult(null)
  }
  const eligible = enabled && name !== undefined && (edited || name !== initialName) && !!name.trim()
    && !classified && !chosen && dismissedName !== name
  useEffect(() => {
    if (!eligible || name === undefined) return
    const controller = new AbortController()
    let active = true
    const timer = window.setTimeout(() => {
      void api.recommendTaskType(name, controller.signal).then(reply => {
        if (active && !manual.current && reply.reason === 'recommended' && reply.task_type_id != null && (reply.confidence ?? 0) >= 0.8) {
          setResult({ name, catalog, id: reply.task_type_id })
        }
      }).catch(() => { /* Optional recommendation: manual selection remains usable. */ })
    }, 500)
    return () => { active = false; clearTimeout(timer); controller.abort() }
  }, [name, catalog, eligible])
  return {
    recommendation: eligible && result?.name === name && result.catalog === catalog
      ? types.find(t => t.id === result.id && t.name !== 'unspecified') : undefined,
    markChosen: () => { manual.current = true; setChosen(true) },
    dismiss: () => setDismissedName(name ?? null),
  }
}
