import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchJson } from '../../lib/api/client'

type Failure = {
  id: string; command: string | null; occurred_at: string; reason: string; status: string;
  dismissed: boolean; recorded_at: string | null; review_id: number | null;
  history: { at: string; command: string | null; reason: string }[];
}
type FailurePage = { items: Failure[]; next_before: string | null }

export function QuickFailures({ history, timezone }: { history: boolean; timezone: string }) {
  const [page, setPage] = useState<FailurePage>({ items: [], next_before: null })
  const [error, setError] = useState(''), [busy, setBusy] = useState(false)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => {
    let current = true
    fetchJson<FailurePage>(`/assistant/quick-failures?history=${history}`).then(data => {
      if (current) { setPage(data); setError('') }
    }).catch(() => { if (current) setError('Could not load command history.') })
    return () => { current = false }
  }, [history, refresh])
  const dismiss = useCallback(async (id: string) => {
    setBusy(true)
    try {
      await fetchJson(`/assistant/quick-failures/${id}/dismiss`, { method: 'POST', body: '{}' })
      setPage(old => ({ ...old, items: old.items.filter(item => item.id !== id) }))
    } catch { setError('Could not dismiss this command. Try again.') }
    finally { setBusy(false) }
  }, [])
  const date = (value: string) => new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(Date.parse(value) === 0 ? new Date() : new Date(value))
  const time = (value: string) => Date.parse(value) === 0 ? 'Original time unavailable' : new Intl.DateTimeFormat(undefined, { timeZone: timezone, dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
  return <section aria-label="Quick Tracking commands" className="quick-failure-history">
    <div className="assistant-review-heading"><h3>{history ? 'Command history' : 'Commands needing attention'}</h3><button disabled={busy} onClick={() => setRefresh(value => value + 1)}>Refresh commands</button></div>
    {error && <p role="alert">{error}</p>}
    {page.items.map(item => <article className="assistant-review-row" key={item.id}><div>
      <blockquote>{item.command ? `“${item.command}”` : 'No transcript captured'}</blockquote>
      <p>{time(item.occurred_at)}</p><p>{item.reason}</p>
      {item.status === 'uncertain' && <p>Waiting to check recording. Reconnect the originating phone before retrying.</p>}
      {!history && <p>{item.command ? 'Retry on the originating phone. If it is unavailable, correct the activity in Day.' : 'Speak again on the originating phone.'}</p>}
      {item.history.length > 0 && <details><summary>Attempt history</summary>{item.history.map((event, index) => <div key={index}><p>{time(event.at)} · {event.command || 'No transcript captured'}</p><p>{event.reason}</p></div>)}</details>}
      <div className="assistant-row-actions"><Link to={`/day/${date(item.recorded_at || item.occurred_at)}`}>{item.status === 'recorded' ? 'View recorded activity' : 'Open Day'}</Link>
      {!history && <button disabled={busy} onClick={() => void dismiss(item.id)}>Dismiss</button>}</div>
    </div></article>)}
    {page.next_before && <button disabled={busy} onClick={async () => {
      setBusy(true)
      try {
        const data = await fetchJson<FailurePage>(`/assistant/quick-failures?history=${history}&before=${page.next_before}`)
        setPage(old => ({ ...data, items: [...old.items, ...data.items] }))
      } catch { setError('Could not load earlier commands.') }
      finally { setBusy(false) }
    }}>Earlier commands</button>}
  </section>
}
