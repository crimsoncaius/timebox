import { useEffect, useState } from 'react'
import { api, type TaskType } from '../lib/api'

/** A request belongs to one open picker and one exact context/catalog snapshot. */
export function useTaskTypeRecommendation(name: string | undefined, types: TaskType[], selectedId: number | null, enabled: boolean, query = '', linkedTaskName?: string) {
  const context = JSON.stringify({ name: name?.trim() || undefined, picker_query: query.trim() || undefined, linked_task_name: linkedTaskName?.trim() || undefined })
  const catalog = JSON.stringify(types.map(t => [t.id, t.name]).sort((a, b) => Number(a[0]) - Number(b[0])))
  const key = JSON.stringify([context, catalog])
  const [dismissed, setDismissed] = useState<string | null>(null)
  const [result, setResult] = useState<{ key: string; id: number } | null>(null)
  const [seen, setSeen] = useState({ context, enabled })
  if (seen.context !== context || seen.enabled !== enabled) {
    setSeen({ context, enabled })
    setDismissed(null)
    setResult(null)
  }
  const eligible = enabled && context !== '{}' && dismissed !== key
  useEffect(() => {
    if (!eligible) return
    const controller = new AbortController()
    let active = true
    const timer = window.setTimeout(() => {
      void api.recommendTaskType(JSON.parse(context), controller.signal).then(reply => {
        if (active && reply.reason === 'recommended' && reply.task_type_id != null && (reply.confidence ?? 0) >= 0.8) {
          setResult({ key, id: reply.task_type_id })
        }
      }).catch(() => { /* Optional prediction; manual selection stays available. */ })
    }, 500)
    return () => { active = false; clearTimeout(timer); controller.abort() }
  }, [context, key, eligible])
  return {
    recommendation: eligible && result?.key === key
      ? types.find(t => t.id === result.id && t.id !== selectedId && t.name !== 'unspecified') : undefined,
    dismiss: () => setDismissed(key),
  }
}
