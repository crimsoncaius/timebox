// Three Work Mode variants, switchable via ?variant=, on the existing /day/:date route.
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { PrototypeSwitcher, type PrototypeVariant } from '../../components/PrototypeSwitcher'

type ItemKind = 'task' | 'non-task'

interface PrototypeState {
  kind: ItemKind
  running: boolean
  taskComplete: boolean
  futurePlansPresent: boolean
  actualStart: string
  actualEnd: string
  notice: string | null
  subtasks: Array<{ id: number; title: string; checked: boolean }>
}

const variants: PrototypeVariant[] = [
  { key: 'A', name: 'Inspector rail' },
  { key: 'B', name: 'Focus canvas' },
  { key: 'C', name: 'Action sheet' },
]

function initialState(kind: ItemKind = 'task'): PrototypeState {
  return {
    kind,
    running: true,
    taskComplete: false,
    futurePlansPresent: true,
    actualStart: '10:12',
    actualEnd: '',
    notice: null,
    subtasks: [
      { id: 1, title: 'Pull revenue numbers', checked: true },
      { id: 2, title: 'Make charts', checked: true },
      { id: 3, title: 'Write conclusion', checked: false },
      { id: 4, title: 'Proofread', checked: false },
    ],
  }
}

export function WorkModePrototype({ date }: { date: string }) {
  const [searchParams] = useSearchParams()
  const requested = (searchParams.get('variant') ?? 'A').toUpperCase()
  const variant = variants.some((entry) => entry.key === requested) ? requested : 'A'
  const [state, setState] = useState<PrototypeState>(() => initialState())

  const actions = {
    setKind(kind: ItemKind) {
      setState(initialState(kind))
    },
    toggleSubtask(id: number) {
      setState((current) => current.taskComplete
        ? current
        : {
            ...current,
            subtasks: current.subtasks.map((subtask) =>
              subtask.id === id ? { ...subtask, checked: !subtask.checked } : subtask,
            ),
          })
    },
    finish() {
      setState((current) => ({
        ...current,
        running: false,
        actualEnd: '11:18',
        notice: 'Actual recorded · Task remains open',
      }))
    },
    finishAndComplete() {
      setState((current) => ({
        ...current,
        running: false,
        actualEnd: '11:18',
        taskComplete: true,
        futurePlansPresent: false,
        notice: 'Task completed · 2 future Planned Blocks removed',
      }))
    },
    undoCompletion() {
      setState((current) => ({
        ...current,
        taskComplete: false,
        futurePlansPresent: true,
        notice: 'Task Completion undone · ended Actual preserved',
      }))
    },
    startAnother() {
      setState((current) => ({
        ...current,
        running: true,
        actualStart: '11:32',
        actualEnd: '',
        notice: null,
      }))
    },
    setActualStart(actualStart: string) {
      setState((current) => ({ ...current, actualStart }))
    },
    setActualEnd(actualEnd: string) {
      setState((current) => ({ ...current, actualEnd }))
    },
    reset() {
      setState(initialState(state.kind))
    },
  }

  const shared = { state, actions, date }

  return (
    <>
      {variant === 'A' ? <VariantA {...shared} /> : null}
      {variant === 'B' ? <VariantB {...shared} /> : null}
      {variant === 'C' ? <VariantC {...shared} /> : null}
      <PrototypeSwitcher variants={variants} current={variant} />
    </>
  )
}

type VariantProps = {
  state: PrototypeState
  actions: {
    setKind: (kind: ItemKind) => void
    toggleSubtask: (id: number) => void
    finish: () => void
    finishAndComplete: () => void
    undoCompletion: () => void
    startAnother: () => void
    setActualStart: (value: string) => void
    setActualEnd: (value: string) => void
    reset: () => void
  }
  date: string
}

function VariantA({ state, actions, date }: VariantProps) {
  return (
    <Layout mainClassName="w-full max-w-none px-5 py-8 lg:px-8">
      <PrototypeBanner onReset={actions.reset} />
      <DayHeading date={date} />
      <ScenarioTabs state={state} onSelect={actions.setKind} />
      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_25rem]">
        <FakeTimeline state={state} />
        <aside className="rounded-2xl border border-outline-variant/25 bg-surface-container-lowest p-5 shadow-sm dark:border-dark-outline-variant dark:bg-dark-surface-container-low">
          <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-on-surface-variant">Work Mode</p>
          <WorkHeader state={state} compact />
          <SubtaskList state={state} onToggle={actions.toggleSubtask} />
          <ActualEditor state={state} actions={actions} />
          <ActionButtons state={state} actions={actions} orientation="stacked" />
          <Notice state={state} onUndo={actions.undoCompletion} />
          <StateReadout state={state} />
        </aside>
      </div>
    </Layout>
  )
}

function VariantB({ state, actions, date }: VariantProps) {
  return (
    <Layout mainClassName="w-full max-w-none px-5 py-8 lg:px-8">
      <DayHeading date={date} />
      <div className="pointer-events-none opacity-30 blur-[1px]">
        <FakeTimeline state={state} />
      </div>
      <div className="fixed inset-0 z-60 overflow-y-auto bg-surface/96 px-4 py-6 backdrop-blur-xl dark:bg-dark-background/96 lg:left-64">
        <div className="mx-auto flex min-h-full max-w-4xl flex-col">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-on-surface-variant">Focused Work Mode</p>
              <p className="mt-1 text-sm text-on-surface-variant">Everything else is quiet until you finish.</p>
            </div>
            <button type="button" className="rounded-full border border-outline-variant/30 px-4 py-2 text-xs" onClick={actions.reset}>Reset demo</button>
          </div>
          <ScenarioTabs state={state} onSelect={actions.setKind} />
          <div className="flex flex-1 flex-col justify-center py-8">
            <div className="grid items-start gap-10 lg:grid-cols-[1fr_20rem]">
              <div>
                <WorkHeader state={state} />
                <div className="mt-8">
                  <SubtaskList state={state} onToggle={actions.toggleSubtask} spacious />
                </div>
              </div>
              <div className="rounded-3xl bg-surface-container-low p-6 dark:bg-dark-surface-container-low">
                <ActualEditor state={state} actions={actions} large />
                <ActionButtons state={state} actions={actions} orientation="stacked" />
                <Notice state={state} onUndo={actions.undoCompletion} />
              </div>
            </div>
          </div>
          <StateReadout state={state} />
        </div>
      </div>
    </Layout>
  )
}

function VariantC({ state, actions, date }: VariantProps) {
  return (
    <Layout mainClassName="w-full max-w-none px-5 py-8 lg:px-8">
      <PrototypeBanner onReset={actions.reset} />
      <DayHeading date={date} />
      <div className="mt-6 pb-[34rem]">
        <FakeTimeline state={state} />
      </div>
      <section className="fixed inset-x-2 bottom-16 z-60 max-h-[72vh] overflow-y-auto rounded-3xl border border-outline-variant/25 bg-surface-container-lowest p-4 shadow-2xl dark:border-dark-outline-variant dark:bg-dark-surface-container-low lg:left-[calc(16rem+1rem)] lg:right-4">
        <div className="mx-auto max-w-5xl">
          <div className="mx-auto mb-3 h-1 w-12 rounded-full bg-outline-variant/50" />
          <ScenarioTabs state={state} onSelect={actions.setKind} />
          <div className="grid gap-5 lg:grid-cols-[1fr_1fr_auto] lg:items-start">
            <WorkHeader state={state} compact />
            <SubtaskList state={state} onToggle={actions.toggleSubtask} compact />
            <div className="min-w-64">
              <ActualEditor state={state} actions={actions} compact />
              <ActionButtons state={state} actions={actions} orientation="inline" />
            </div>
          </div>
          <Notice state={state} onUndo={actions.undoCompletion} />
          <StateReadout state={state} />
        </div>
      </section>
    </Layout>
  )
}

function PrototypeBanner({ onReset }: { onReset: () => void }) {
  return (
    <div className="mb-5 flex items-center justify-between gap-3 rounded-xl border border-dashed border-outline-variant/50 bg-surface-container-low px-4 py-2 text-xs text-on-surface-variant">
      <span><strong>PROTOTYPE</strong> · UI only, state resets on reload.</span>
      <button type="button" className="underline" onClick={onReset}>Reset</button>
    </div>
  )
}

function DayHeading({ date }: { date: string }) {
  return (
    <header>
      <p className="text-xs uppercase tracking-[0.16em] text-on-surface-variant">Day · {date}</p>
      <h1 className="mt-1 font-headline text-4xl font-extralight tracking-tight">Wednesday, August 26</h1>
    </header>
  )
}

function ScenarioTabs({ state, onSelect }: { state: PrototypeState; onSelect: (kind: ItemKind) => void }) {
  return (
    <div className="mt-5 inline-flex rounded-full bg-surface-container p-1 text-xs">
      <button type="button" className={`rounded-full px-4 py-2 ${state.kind === 'task' ? 'bg-surface-container-lowest shadow-sm' : 'text-on-surface-variant'}`} onClick={() => onSelect('task')}>Battle Plan Task</button>
      <button type="button" className={`rounded-full px-4 py-2 ${state.kind === 'non-task' ? 'bg-surface-container-lowest shadow-sm' : 'text-on-surface-variant'}`} onClick={() => onSelect('non-task')}>Non-task item</button>
    </div>
  )
}

function FakeTimeline({ state }: { state: PrototypeState }) {
  const title = state.kind === 'task' ? 'Prepare investor presentation' : 'Dinner with Alex'
  return (
    <section className="min-h-[34rem] rounded-2xl border border-outline-variant/20 bg-surface-container-low p-5 dark:border-dark-outline-variant dark:bg-dark-surface-container-low">
      <div className="grid grid-cols-[4rem_1fr_1fr] gap-3 text-[10px] uppercase tracking-widest text-on-surface-variant">
        <span />
        <span>Planned</span>
        <span>Actual</span>
      </div>
      <div className="mt-3 grid grid-cols-[4rem_1fr_1fr] gap-3 border-t border-outline-variant/25 pt-4">
        <span className="pt-2 text-xs text-on-surface-variant">10:00</span>
        <div className="min-h-32 rounded-xl border border-planned-border bg-planned-surface p-3 text-planned">
          <p className="text-xs font-semibold">10:00–11:30</p>
          <p className="mt-2 text-sm font-medium text-on-surface">{title}</p>
          <p className="mt-1 text-xs text-on-surface-variant">Planned Block</p>
        </div>
        <div className="min-h-32 rounded-xl border border-actual-border bg-actual-surface p-3 text-actual">
          <p className="text-xs font-semibold">{state.actualStart}–{state.running ? 'Now' : state.actualEnd || '—'}</p>
          <p className="mt-2 text-sm font-medium text-on-surface">{title}</p>
          <p className="mt-1 text-xs text-on-surface-variant">{state.running ? 'Actual Block · running' : 'Actual Block'}</p>
        </div>
      </div>
      <div className="mt-5 grid grid-cols-[4rem_1fr_1fr] gap-3 border-t border-outline-variant/20 pt-4 opacity-70">
        <span className="pt-2 text-xs text-on-surface-variant">14:00</span>
        <div className="min-h-24 rounded-xl border border-planned-border bg-planned-surface p-3 text-sm text-on-surface">
          {state.futurePlansPresent ? 'Follow-up Planned Block' : 'Removed by Task Completion'}
        </div>
        <div />
      </div>
    </section>
  )
}

function WorkHeader({ state, compact = false }: { state: PrototypeState; compact?: boolean }) {
  const title = state.kind === 'task' ? 'Prepare investor presentation' : 'Dinner with Alex'
  return (
    <header className={compact ? 'mt-3' : ''}>
      <div className="flex items-center gap-2 text-xs text-on-surface-variant">
        <span className={`h-2 w-2 rounded-full ${state.running ? 'bg-actual' : 'bg-outline'}`} />
        {state.running ? 'Actual recording live' : 'Actual recorded'}
      </div>
      <h2 className={`${compact ? 'mt-2 text-2xl' : 'mt-4 text-5xl'} font-headline font-extralight leading-tight tracking-tight`}>{title}</h2>
      <p className="mt-2 text-sm text-on-surface-variant">
        {state.kind === 'task' ? '2 of 4 Subtasks checked' : 'Scheduled item · no Task Completion'}
      </p>
      {state.taskComplete ? <span className="mt-3 inline-flex rounded-full bg-primary-container px-3 py-1 text-xs font-medium">Task complete</span> : null}
    </header>
  )
}

function SubtaskList({
  state,
  onToggle,
  spacious = false,
  compact = false,
}: {
  state: PrototypeState
  onToggle: (id: number) => void
  spacious?: boolean
  compact?: boolean
}) {
  if (state.kind === 'non-task') {
    return <p className="mt-5 rounded-xl bg-surface-container-low p-4 text-sm text-on-surface-variant">Non-task scheduled items record Actual time but have no Subtasks or Task Completion.</p>
  }
  return (
    <div className={`${compact ? '' : 'mt-5'} space-y-1`}>
      {state.subtasks.map((subtask) => (
        <label key={subtask.id} className={`flex items-center gap-3 rounded-xl hover:bg-surface-container-low ${spacious ? 'px-4 py-3 text-base' : 'px-2 py-2 text-sm'} ${state.taskComplete ? 'opacity-60' : ''}`}>
          <input type="checkbox" checked={subtask.checked} disabled={state.taskComplete} onChange={() => onToggle(subtask.id)} />
          <span className={subtask.checked ? 'line-through text-on-surface-variant' : ''}>{subtask.title}</span>
        </label>
      ))}
    </div>
  )
}

function ActualEditor({
  state,
  actions,
  large = false,
  compact = false,
}: {
  state: PrototypeState
  actions: VariantProps['actions']
  large?: boolean
  compact?: boolean
}) {
  return (
    <section className={`${compact ? '' : 'mt-5'} rounded-2xl border border-actual-border bg-actual-surface p-4`}>
      <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-actual">Actual time</p>
      <div className={`mt-2 ${large ? 'grid gap-2 text-2xl' : 'flex items-end gap-1 text-lg'} font-headline font-light`}>
        <label className="min-w-0 flex-1">
          <span className="sr-only">Actual start</span>
          <input className="min-w-0 w-full border-b border-actual-border bg-transparent py-1" type="time" value={state.actualStart} onChange={(event) => actions.setActualStart(event.target.value)} />
        </label>
        <span className={`${large ? 'hidden' : ''} pb-1 text-on-surface-variant`}>–</span>
        <label className="min-w-0 flex-1">
          <span className="sr-only">Actual end</span>
          <input className="min-w-0 w-full border-b border-actual-border bg-transparent py-1" type="time" value={state.actualEnd} placeholder={state.running ? 'Now' : ''} disabled={state.running} onChange={(event) => actions.setActualEnd(event.target.value)} />
        </label>
      </div>
      <p className="mt-2 text-xs text-on-surface-variant">Editable now or retrospectively. Planned time is unchanged.</p>
    </section>
  )
}

function ActionButtons({ state, actions, orientation }: { state: PrototypeState; actions: VariantProps['actions']; orientation: 'stacked' | 'inline' }) {
  if (!state.running) {
    return (
      <button type="button" className="mt-4 w-full rounded-xl bg-primary px-4 py-3 text-sm font-medium text-on-primary disabled:opacity-40" disabled={state.taskComplete} onClick={actions.startAnother}>
        Start another Actual Block
      </button>
    )
  }
  const classes = orientation === 'stacked' ? 'mt-4 grid gap-2' : 'mt-3 flex flex-wrap gap-2'
  return (
    <div className={classes}>
      <button type="button" className="flex-1 rounded-xl border border-outline-variant/40 px-4 py-3 text-sm font-medium" onClick={actions.finish}>Finish session</button>
      {state.kind === 'task' ? (
        <button type="button" className="flex-1 rounded-xl bg-primary px-4 py-3 text-sm font-medium text-on-primary" onClick={actions.finishAndComplete}>Finish + complete Task</button>
      ) : null}
    </div>
  )
}

function Notice({ state, onUndo }: { state: PrototypeState; onUndo: () => void }) {
  if (!state.notice) return null
  return (
    <div className="mt-4 flex items-center justify-between gap-3 rounded-xl bg-inverse-surface px-4 py-3 text-xs text-white">
      <span>{state.notice}</span>
      {state.taskComplete ? <button type="button" className="font-semibold underline" onClick={onUndo}>Undo</button> : null}
    </div>
  )
}

function StateReadout({ state }: { state: PrototypeState }) {
  return (
    <details className="mt-4 rounded-xl border border-dashed border-outline-variant/40 p-3 text-[11px] text-on-surface-variant">
      <summary className="cursor-pointer font-semibold uppercase tracking-wider">Prototype state</summary>
      <pre className="mt-2 overflow-x-auto whitespace-pre-wrap">{JSON.stringify(state, null, 2)}</pre>
    </details>
  )
}
