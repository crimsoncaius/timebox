// Regression coverage for the November 2026 web audit.
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { TodayPage } from '../features/today/TodayPage'
import { BattlePlanPage } from '../features/battle-plan/BattlePlanPage'
import { RecurringPage } from '../features/battle-plan/RecurringPage'
import { TaskDetailPanel } from '../features/battle-plan/TaskDetailPanel'
import type { BattleTask } from '../lib/api'

const fakeActivity = vi.hoisted(() => ({
  snapshot: { protocol: 'activity-online-v1', reporting_timezone: 'UTC', server_at: '2026-06-01T12:00:00Z', current: null, records: [], cursor: 1 },
  pending: false, error: null,
}))
vi.mock('../features/activity/activityRepository', async original => ({
  ...await original<object>(),
  getActivityRepository: () => ({ subscribe: () => () => {}, getSnapshot: () => fakeActivity,
    now: () => Date.parse('2026-06-01T12:00:00Z'), refresh: async () => {}, state: fakeActivity }),
}))
vi.mock('../components/Layout', () => ({ Layout: ({ children }: { children: React.ReactNode }) => <main>{children}</main> }))
vi.mock('../features/activity/ActivityTracking', () => ({ ActivityTracking: () => null }))
vi.mock('../features/calendar/GoogleCalendar', () => ({ GoogleCalendar: () => null }))
vi.mock('../components/DayTimeline', () => ({ DayTimeline: ({day}: {day: {time_blocks: {name: string}[]}}) => <div data-testid="plans">{day.time_blocks.map(b => b.name).join(',')}</div> }))

const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), {status, headers: {'Content-Type':'application/json'}})
function deferred<T>() { let resolve!: (value:T)=>void; const promise = new Promise<T>(r => {resolve=r}); return {resolve,promise} }
const type = {id:1, name:'work', created_at:'',updated_at:''}
const day = (date: string, name: string) => ({id:1,date,created_at:'',updated_at:'',actual_blocks:[],actual_minutes:0,
  meta:{timezone:'UTC',today:'2026-06-01',server_now_iso:'2026-06-01T12:00:00Z'},
  time_blocks:[{id:1,lane:'planned',name,task_type_id:1,task_type:type,start_minute:480,end_minute:510,created_at:'',updated_at:''}]})
beforeEach(() => { localStorage.clear(); window.confirm = vi.fn(() => true) })
afterEach(() => {vi.unstubAllGlobals(); vi.restoreAllMocks()})

function RouteControls(){ const navigate=useNavigate(); return <button onClick={() => void navigate('/day/2026-06-02')}>Browse next route</button> }
it('W1: preserves June 2 plans after a slower June 1 load completes', async () => {
  const first=deferred<Response>()
  vi.stubGlobal('fetch',vi.fn(async (input:RequestInfo|URL) => {
    const url=String(input)
    if(url.endsWith('/days/2026-06-01'))return first.promise
    if(url.endsWith('/days/2026-06-02'))return response(day('2026-06-02','Second day plan'))
    if(url.includes('/task-types'))return response([type])
    if(url.includes('/tasks'))return response({items:[],timezone:'UTC',server_now_iso:'2026-06-01T12:00:00Z'})
    throw Error(url)
  }))
  render(<MemoryRouter initialEntries={['/day/2026-06-01']}><RouteControls/><Routes><Route path="/day/:date" element={<TodayPage/>}/></Routes></MemoryRouter>)
  fireEvent.click(screen.getByText('Browse next route'))
  await waitFor(() => expect(screen.getByTestId('plans')).toHaveTextContent('Second day plan'))
  await act(async () => first.resolve(response(day('2026-06-01','First day plan'))))
  expect(screen.getByTestId('day-date')).toHaveTextContent('2026-06-02')
  expect(screen.getByTestId('plans')).toHaveTextContent('Second day plan')
})

it('W2: retains both successful subtask checks when replies overlap', async () => {
  const first=deferred<Response>(), second=deferred<Response>()
  const subtasks=[21,22].map((id,index)=>({id,parent_task_id:11,title:`Checkpoint ${index+1}`,checked:false,effectively_resolved:false,position:index,created_at:'',updated_at:''}))
  const task={id:11,parent_id:null,project_id:null,project:null,task_type_id:1,task_type:type,title:'Audit parent',description:'',status:'open',urgency:null,importance:null,deadline_date:null,deadline_at:null,reminder_at:null,reminder_delivered_at:null,reminder_skipped_at:null,position:0,archived_at:null,deleted_at:null,created_at:'',updated_at:'',overdue:false,subtasks,ready_to_plan:false}
  vi.stubGlobal('fetch',vi.fn(async (input:RequestInfo|URL) => {
    const url=String(input)
    if(url.endsWith('/projects'))return response([])
    if(url.endsWith('/task-types'))return response([type])
    if(url.includes('/tasks?'))return response({items:[task],timezone:'UTC',server_now_iso:'2026-06-01T12:00:00Z'})
    if(url.endsWith('/subtasks/21/check'))return first.promise
    if(url.endsWith('/subtasks/22/check'))return second.promise
    throw Error(url)
  }))
  render(<MemoryRouter><BattlePlanPage/></MemoryRouter>)
  const card=await screen.findByRole('article',{name:'Move Audit parent'})
  fireEvent.click(within(card).getByRole('button',{name:/0 of 2 subtasks/}))
  fireEvent.click(screen.getByRole('checkbox',{name:'Check subtask Checkpoint 1'}))
  fireEvent.click(screen.getByRole('checkbox',{name:'Check subtask Checkpoint 2'}))
  await act(async()=>first.resolve(response({...subtasks[0],checked:true})))
  expect(screen.getByRole('checkbox',{name:'Uncheck subtask Checkpoint 1'})).toBeChecked()
  await act(async()=>second.resolve(response({...subtasks[1],checked:true})))
  expect(screen.getByRole('checkbox',{name:/subtask Checkpoint 1/})).toBeChecked()
  expect(screen.getByRole('checkbox',{name:/subtask Checkpoint 2/})).toBeChecked()
})

it('W3: shows a recovery action when the health/date lookup fails',async()=>{
  let healthDown = true
  vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
    const url=String(input)
    if(url.endsWith('/health'))return healthDown ? response({detail:'Temporary failure'},503) : response({today:'2026-06-01',timezone:'UTC'})
    if(url.includes('/recurring-templates')||url.endsWith('/projects')||url.endsWith('/task-types'))return response([])
    throw Error(url)
  }))
  render(<MemoryRouter><RecurringPage/></MemoryRouter>)
  await screen.findByText('No active recurring tasks')
  expect(screen.getByRole('button',{name:'New recurring task'})).toBeDisabled()
  expect(screen.queryByRole('alert'), 'A disabled create action needs a visible error/retry explanation').not.toBeNull()
  healthDown = false
  fireEvent.click(screen.getByRole('button',{name:'Retry date'}))
  await waitFor(()=>expect(screen.getByRole('button',{name:'New recurring task'})).toBeEnabled())
})

it('W4: confines keyboard focus to the recurring editor',async()=>{
  vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
    const url=String(input)
    if(url.endsWith('/health'))return response({today:'2026-06-01',timezone:'UTC'})
    if(url.includes('/recurring-templates')||url.endsWith('/projects')||url.endsWith('/task-types'))return response([])
    throw Error(url)
  }))
  const user=userEvent.setup()
  render(<MemoryRouter><RecurringPage/></MemoryRouter>)
  await waitFor(()=>expect(screen.getByRole('button',{name:'New recurring task'})).toBeEnabled())
  await user.click(screen.getByRole('button',{name:'New recurring task'}))
  const dialog=screen.getByRole('dialog',{name:'New recurring task'})
  const close=within(dialog).getByRole('button',{name:'Close recurring form'})
  close.focus()
  await user.tab({shift:true})
  expect(dialog.contains(document.activeElement)).toBe(true)
})

it('W5: refuses to save a task while its visible deadline input is invalid', async()=>{
  const task: BattleTask={id:11,parent_id:null,project_id:null,project:null,task_type_id:1,task_type:type,title:'Audit task',description:'',status:'open',urgency:null,importance:null,deadline_date:'2026-06-02',deadline_at:null,reminder_at:null,reminder_delivered_at:null,reminder_skipped_at:null,position:0,archived_at:null,deleted_at:null,created_at:'',updated_at:'',overdue:false,subtasks:[],ready_to_plan:false}
  const patch=vi.fn(async()=>{}), close=vi.fn()
  render(<MemoryRouter><TaskDetailPanel task={task} readinessPending={false} projects={[]} taskTypes={[type]} timezone="UTC" serverNowIso="2026-06-01T12:00:00Z" onClose={close} onPatch={patch} onSetCompletion={async()=>{}} onSetSubtaskChecked={async()=>{}} onAddSubtask={async()=>{}} onTrash={async()=>{}} onCreateTaskTypePath={async()=>type}/></MemoryRouter>)
  fireEvent.change(screen.getByRole('textbox',{name:'Title'}),{target:{value:'Edited audit task'}})
  fireEvent.change(screen.getByRole('textbox',{name:'Deadline date'}),{target:{value:'2026-06-99'}})
  expect(screen.getByRole('textbox',{name:'Deadline date'})).toHaveAttribute('aria-invalid','true')
  await act(async()=>fireEvent.click(screen.getByRole('button',{name:'Save'})))
  expect(patch).not.toHaveBeenCalled()
  expect(close).not.toHaveBeenCalled()
})
