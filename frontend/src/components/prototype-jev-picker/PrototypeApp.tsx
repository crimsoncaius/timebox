// THROWAWAY: three picker presentations on the existing /battle-plan route.
// All API calls are intercepted in this mode. Data and edits live only in memory.
import { BrowserRouter } from 'react-router-dom'
import { BattlePlanPage } from '../../features/battle-plan/BattlePlanPage'
import { ReadinessProvider } from '../../features/readiness/ReadinessProvider'
import type { BattleTask, TaskType } from '../../lib/api'
import { PrototypeSwitcher } from './PrototypeSwitcher'
import './prototype.css'

export const fixtureTypes: TaskType[] = ['work', 'work/deep', 'work/planning', 'learning', 'learning/music', 'learning/reading', 'health', 'health/exercise', 'admin', 'admin/finances'].map((name, i) => ({ id: i + 1, name, usage_count: 30 - i, created_at: '', updated_at: '' }))

export function installPrototypeApi() {
  const project = { id: 7, name: 'Personal growth', created_at: '', updated_at: '' }
  let tasks: BattleTask[] = ['Practice piano scales', 'Read the next chapter', 'Plan next week', 'Review monthly expenses'].map((title, i) => ({
    id: 11 + i, parent_id: null, project_id: 7, project, title, description: i === 0 ? 'Twenty minutes of scales, then work through the difficult passage.\n\nChoose a Task Type in the properties panel.' : '',
    task_type_id: i === 0 ? 2 : null, task_type: i === 0 ? fixtureTypes[1] : null,
    status: 'open', urgency: null, importance: null, deadline_date: null, deadline_at: null,
    reminder_at: null, reminder_delivered_at: null, reminder_skipped_at: null, position: i,
    archived_at: null, deleted_at: null, created_at: '', updated_at: '', overdue: false, subtasks: [],
  }))
  globalThis.fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, location.origin)
    const path = url.pathname.replace(/^\/api/, '')
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : {}
    let data: unknown = []
    if (path === '/health') data = { status: 'ok', today: '2026-09-24', timezone: 'Asia/Singapore' }
    else if (path === '/projects') data = [project]
    else if (path === '/task-types') {
      if (method === 'POST') { const row = { id: 100 + fixtureTypes.length, name: body.name, created_at: '', updated_at: '' }; fixtureTypes.push(row); data = row }
      else data = fixtureTypes
    } else if (/^\/tasks\/\d+$/.test(path)) {
      const id = Number(path.split('/').pop())
      if (method === 'PATCH') tasks = tasks.map(task => task.id === id ? { ...task, ...body, task_type: fixtureTypes.find(type => type.id === ('task_type_id' in body ? body.task_type_id : task.task_type_id)) ?? null } : task)
      data = tasks.find(task => task.id === id)
    } else if (path === '/tasks') data = { items: url.searchParams.get('state') === 'active' ? tasks : [], timezone: 'Asia/Singapore', server_now_iso: new Date().toISOString() }
    else if (path.includes('recommendation')) data = { task_type_id: null, confidence: null }
    return new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } })
  }
}

export function JevPickerPrototypeApp() {
  return <BrowserRouter><ReadinessProvider><BattlePlanPage /><PrototypeSwitcher /></ReadinessProvider></BrowserRouter>
}
