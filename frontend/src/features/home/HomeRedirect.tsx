import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { api } from '../../lib/api'
import { Layout } from '../../components/Layout'

export function HomeRedirect() {
  const [target, setTarget] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void api
      .health()
      .then((h) => setTarget(h.today))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not reach API'))
  }, [])

  if (error) {
    return (
      <Layout>
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-red-800 dark:border-red-900 dark:bg-red-950/50 dark:text-red-200">
          <p className="font-medium">Cannot load today from server.</p>
          <p className="mt-1 text-sm">{error}</p>
          <p className="mt-2 text-sm text-zinc-700 dark:text-zinc-300">
            Start the API (see README) and ensure the Vite proxy points to it, or set <code>VITE_API_BASE_URL</code>.
          </p>
        </div>
      </Layout>
    )
  }

  if (!target) {
    return (
      <Layout>
        <p className="text-zinc-600">Loading today…</p>
      </Layout>
    )
  }

  return <Navigate to={`/day/${target}`} replace />
}
