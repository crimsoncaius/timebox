/**
 * PROTOTYPE — throwaway UI, never production implementation.
 * Three variants of the Android Battle Plan Task composer, switchable via
 * `?prototype=create-task&variant=`, on the existing `/battle-plan` route.
 */
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

type Priority = 'Unset' | 'Low' | 'Medium' | 'High'
type Status = 'Open' | 'In Progress'
type DeadlineMode = 'None' | 'Date only' | 'Date and time'

type Draft = {
  title: string
  description: string
  location: string
  taskType: string
  status: Status
  deadlineMode: DeadlineMode
  deadlineDate: string
  deadlineTime: string
  urgency: Priority
  importance: Priority
  readyToPlan: boolean
  reminder: boolean
  reminderDate: string
  reminderTime: string
  moreOpen: boolean
}

const initialDraft: Draft = {
  title: '',
  description: '',
  location: 'Admin',
  taskType: 'Unset',
  status: 'Open',
  deadlineMode: 'None',
  deadlineDate: '',
  deadlineTime: '',
  urgency: 'Unset',
  importance: 'Unset',
  readyToPlan: false,
  reminder: false,
  reminderDate: '',
  reminderTime: '',
  moreOpen: false,
}

const variants = [
  { key: 'A', name: 'Editorial sections' },
  { key: 'B', name: 'Command canvas' },
  { key: 'C', name: 'Planning workbench' },
] as const

type VariantKey = (typeof variants)[number]['key']

export function CreateTaskComposerPrototype() {
  const [params] = useSearchParams()
  const requested = params.get('variant')?.toUpperCase()
  const variant: VariantKey = requested === 'B' || requested === 'C' ? requested : 'A'
  const [draft, setDraft] = useState(initialDraft)
  const [dark, setDark] = useState(false)

  const update = <K extends keyof Draft>(key: K, value: Draft[K]) => {
    setDraft((current) => ({ ...current, [key]: value }))
  }

  const props = { draft, update }

  return (
    <main className={dark ? 'dark' : ''}>
      <div className="min-h-screen bg-[#d8d4ca] px-4 pb-32 pt-6 text-[#252929] transition-colors dark:bg-[#090807] dark:text-stone-100 sm:px-8 sm:pt-10">
        <div className="mx-auto flex max-w-5xl flex-col items-center gap-6 lg:flex-row lg:items-start lg:justify-center">
          <div className="w-full max-w-[430px]">
            <p className="mb-3 text-center text-[10px] font-semibold uppercase tracking-[0.24em] text-stone-600 dark:text-stone-400">
              Prototype · native Android composer
            </p>
            {variant === 'A' && <VariantA {...props} />}
            {variant === 'B' && <VariantB {...props} />}
            {variant === 'C' && <VariantC {...props} />}
          </div>
          <StateInspector draft={draft} variant={variant} />
        </div>
        <PrototypeSwitcher variant={variant} dark={dark} onToggleDark={() => setDark((value) => !value)} />
      </div>
    </main>
  )
}

type VariantProps = {
  draft: Draft
  update: <K extends keyof Draft>(key: K, value: Draft[K]) => void
}

function VariantA({ draft, update }: VariantProps) {
  return (
    <PhoneShell className="bg-[#f7f4ea] dark:bg-stone-950">
      <ComposerHeader eyebrow="BATTLE PLAN" title="New task" chips={['All Tasks', draft.status]} />
      <div className="flex-1 space-y-4 overflow-y-auto px-4 pb-28 pt-4">
        <SectionCard number="01" title="Essentials">
          <TextField label="Title" value={draft.title} onChange={(value) => update('title', value)} autoFocus />
          <TextArea label="Description" value={draft.description} onChange={(value) => update('description', value)} />
        </SectionCard>

        <SectionCard number="02" title="Organization">
          <div className="grid grid-cols-2 gap-3">
            <SelectField label="Location" value={draft.location} options={['Admin', 'Timebox', 'Personal']} onChange={(value) => update('location', value)} />
            <SelectField label="Task Type" value={draft.taskType} options={['Unset', 'Development', 'Design', 'Admin / Finance']} onChange={(value) => update('taskType', value)} />
          </div>
          <Segmented label="Status" options={['Open', 'In Progress']} value={draft.status} onChange={(value) => update('status', value as Status)} />
        </SectionCard>

        <SectionCard number="03" title="Planning">
          <DeadlineFields draft={draft} update={update} />
          <Segmented label="Urgency" options={['Unset', 'Low', 'Medium', 'High']} value={draft.urgency} onChange={(value) => update('urgency', value as Priority)} />
          <Segmented label="Importance" options={['Unset', 'Low', 'Medium', 'High']} value={draft.importance} onChange={(value) => update('importance', value as Priority)} />
        </SectionCard>

        <MoreOptions draft={draft} update={update} />
      </div>
      <CreateDock title={draft.title} />
    </PhoneShell>
  )
}

function VariantB({ draft, update }: VariantProps) {
  return (
    <PhoneShell className="bg-[#fbfaf5] dark:bg-stone-950">
      <div className="border-b border-stone-300/80 bg-[#fbfaf5]/95 px-5 pb-4 pt-5 backdrop-blur dark:border-stone-800 dark:bg-stone-950/95">
        <div className="flex items-center justify-between">
          <span className="text-[10px] font-semibold tracking-[0.24em] text-stone-500">NEW BATTLE PLAN TASK</span>
          <button className="grid size-10 place-items-center rounded-full text-xl text-stone-500 hover:bg-stone-200/70 dark:hover:bg-stone-800" type="button" aria-label="Close">×</button>
        </div>
        <input
          className="mt-5 w-full border-0 bg-transparent font-headline text-[34px] font-light leading-tight tracking-[-0.04em] outline-none placeholder:text-stone-400 dark:placeholder:text-stone-700"
          placeholder="What needs doing?"
          value={draft.title}
          onChange={(event) => update('title', event.target.value)}
          autoFocus
        />
        <textarea
          className="mt-3 min-h-16 w-full resize-none border-0 bg-transparent text-sm leading-6 text-stone-600 outline-none placeholder:text-stone-400 dark:text-stone-300 dark:placeholder:text-stone-700"
          placeholder="Add context, notes, or a useful outcome…"
          value={draft.description}
          onChange={(event) => update('description', event.target.value)}
        />
      </div>

      <div className="flex-1 overflow-y-auto pb-28">
        <div className="border-b border-stone-300/70 px-5 py-5 dark:border-stone-800">
          <MicroLabel>File it</MicroLabel>
          <div className="mt-3 grid grid-cols-3 gap-2">
            <CanvasTile icon="folder" label="Location" value={draft.location}>
              <select value={draft.location} onChange={(event) => update('location', event.target.value)}>
                <option>Admin</option><option>Timebox</option><option>Personal</option>
              </select>
            </CanvasTile>
            <CanvasTile icon="label" label="Task Type" value={draft.taskType}>
              <select value={draft.taskType} onChange={(event) => update('taskType', event.target.value)}>
                <option>Unset</option><option>Development</option><option>Design</option><option>Admin / Finance</option>
              </select>
            </CanvasTile>
            <CanvasTile icon="radio_button_checked" label="Status" value={draft.status}>
              <select value={draft.status} onChange={(event) => update('status', event.target.value as Status)}>
                <option>Open</option><option>In Progress</option>
              </select>
            </CanvasTile>
          </div>
        </div>

        <div className="border-b border-stone-300/70 px-5 py-5 dark:border-stone-800">
          <div className="flex items-center justify-between">
            <MicroLabel>Give it a horizon</MicroLabel>
            <span className="text-[11px] text-stone-500">Optional</span>
          </div>
          <div className="mt-3 rounded-2xl bg-stone-100 p-3 dark:bg-stone-900">
            <DeadlineFields draft={draft} update={update} compact />
          </div>
        </div>

        <div className="border-b border-stone-300/70 px-5 py-5 dark:border-stone-800">
          <MicroLabel>Signal</MicroLabel>
          <div className="mt-3 space-y-4">
            <Segmented label="Urgency" options={['Unset', 'Low', 'Medium', 'High']} value={draft.urgency} onChange={(value) => update('urgency', value as Priority)} />
            <Segmented label="Importance" options={['Unset', 'Low', 'Medium', 'High']} value={draft.importance} onChange={(value) => update('importance', value as Priority)} />
          </div>
        </div>

        <div className="px-5 py-5"><MoreOptions draft={draft} update={update} flat /></div>
      </div>
      <CreateDock title={draft.title} label="Create in Open" />
    </PhoneShell>
  )
}

function VariantC({ draft, update }: VariantProps) {
  const [tab, setTab] = useState<'Essentials' | 'Organize' | 'Plan'>('Essentials')
  return (
    <PhoneShell className="bg-[#f3efe2] dark:bg-stone-950">
      <div className="bg-[#292e2f] px-5 pb-5 pt-5 text-stone-50 dark:bg-stone-900">
        <div className="flex items-start justify-between">
          <div>
            <p className="text-[10px] font-semibold tracking-[0.24em] text-stone-400">BATTLE PLAN / CAPTURE</p>
            <h1 className="mt-2 font-headline text-[30px] font-light tracking-[-0.03em]">New task</h1>
          </div>
          <button className="grid size-10 place-items-center rounded-full border border-white/15 text-xl text-stone-300" type="button" aria-label="Close">×</button>
        </div>
        <div className="mt-5 grid grid-cols-3 rounded-xl bg-black/20 p-1">
          {(['Essentials', 'Organize', 'Plan'] as const).map((item, index) => (
            <button
              key={item}
              type="button"
              onClick={() => setTab(item)}
              className={`rounded-lg px-2 py-2.5 text-[11px] font-medium transition ${tab === item ? 'bg-stone-100 text-stone-900 shadow-sm' : 'text-stone-400'}`}
            >
              <span className="mr-1 opacity-60">0{index + 1}</span> {item}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-5 pb-28 pt-5">
        {tab === 'Essentials' && (
          <div className="space-y-5">
            <LedgerHeading number="01" title="Name the outcome" note="A title is all you need for quick capture." />
            <TextField label="Title" value={draft.title} onChange={(value) => update('title', value)} autoFocus />
            <TextArea label="Description" value={draft.description} onChange={(value) => update('description', value)} />
            <button type="button" onClick={() => setTab('Organize')} className="flex w-full items-center justify-between border-t border-stone-400/40 py-4 text-sm font-medium">
              Continue to organization <span>→</span>
            </button>
          </div>
        )}
        {tab === 'Organize' && (
          <div className="space-y-5">
            <LedgerHeading number="02" title="Put it in context" note="Location is editable because this prototype starts in All Tasks." />
            <LedgerRow label="Location"><SelectField value={draft.location} options={['Admin', 'Timebox', 'Personal']} onChange={(value) => update('location', value)} /></LedgerRow>
            <LedgerRow label="Task Type"><SelectField value={draft.taskType} options={['Unset', 'Development', 'Design', 'Admin / Finance']} onChange={(value) => update('taskType', value)} /></LedgerRow>
            <LedgerRow label="Status"><Segmented options={['Open', 'In Progress']} value={draft.status} onChange={(value) => update('status', value as Status)} /></LedgerRow>
            <button type="button" onClick={() => setTab('Plan')} className="flex w-full items-center justify-between border-t border-stone-400/40 py-4 text-sm font-medium">
              Continue to planning <span>→</span>
            </button>
          </div>
        )}
        {tab === 'Plan' && (
          <div className="space-y-5">
            <LedgerHeading number="03" title="Shape the commitment" note="Everything here is optional." />
            <DeadlineFields draft={draft} update={update} />
            <div className="rounded-2xl border border-stone-400/40 bg-[#faf8ef] p-4 dark:border-stone-700 dark:bg-stone-900">
              <Segmented label="Urgency" options={['Unset', 'Low', 'Medium', 'High']} value={draft.urgency} onChange={(value) => update('urgency', value as Priority)} />
              <div className="my-4 h-px bg-stone-300 dark:bg-stone-700" />
              <Segmented label="Importance" options={['Unset', 'Low', 'Medium', 'High']} value={draft.importance} onChange={(value) => update('importance', value as Priority)} />
            </div>
            <MoreOptions draft={draft} update={update} />
          </div>
        )}
      </div>
      <CreateDock title={draft.title} label={tab === 'Essentials' ? 'Quick create' : 'Create task'} />
    </PhoneShell>
  )
}

function PhoneShell({ children, className }: { children: React.ReactNode; className: string }) {
  return (
    <section className={`relative mx-auto flex h-[790px] w-full max-w-[390px] flex-col overflow-hidden rounded-[34px] border-[7px] border-[#171918] shadow-[0_28px_80px_rgba(24,24,20,0.28)] ${className}`}>
      <div className="absolute left-1/2 top-0 z-30 h-[18px] w-[112px] -translate-x-1/2 rounded-b-2xl bg-[#171918]" />
      {children}
    </section>
  )
}

function ComposerHeader({ eyebrow, title, chips }: { eyebrow: string; title: string; chips: string[] }) {
  return (
    <header className="border-b border-stone-300/80 bg-[#f7f4ea]/95 px-5 pb-5 pt-7 backdrop-blur dark:border-stone-800 dark:bg-stone-950/95">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[10px] font-semibold tracking-[0.24em] text-stone-500">{eyebrow}</p>
          <h1 className="mt-2 font-headline text-[32px] font-light tracking-[-0.035em]">{title}</h1>
        </div>
        <button className="grid size-10 place-items-center rounded-full border border-stone-300 text-xl text-stone-500 dark:border-stone-700" type="button" aria-label="Close">×</button>
      </div>
      <div className="mt-4 flex gap-2">
        {chips.map((chip) => <span key={chip} className="rounded-full bg-stone-200/80 px-3 py-1 text-[11px] font-medium text-stone-600 dark:bg-stone-800 dark:text-stone-300">{chip}</span>)}
      </div>
    </header>
  )
}

function SectionCard({ number, title, children }: { number: string; title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-[20px] border border-stone-300/70 bg-[#fffdf7] p-4 shadow-[0_1px_0_rgba(255,255,255,0.9)_inset,0_8px_24px_rgba(60,50,30,0.05)] dark:border-stone-800 dark:bg-stone-900">
      <div className="mb-4 flex items-center gap-3">
        <span className="font-headline text-[11px] text-stone-400">{number}</span>
        <h2 className="text-xs font-semibold uppercase tracking-[0.16em] text-stone-600 dark:text-stone-300">{title}</h2>
        <div className="h-px flex-1 bg-stone-300/80 dark:bg-stone-700" />
      </div>
      <div className="space-y-4">{children}</div>
    </section>
  )
}

function TextField({ label, value, onChange, autoFocus = false }: { label: string; value: string; onChange: (value: string) => void; autoFocus?: boolean }) {
  return (
    <label className="block">
      <FieldLabel>{label}</FieldLabel>
      <input autoFocus={autoFocus} value={value} onChange={(event) => onChange(event.target.value)} placeholder="Prepare launch notes" className="mt-1.5 h-12 w-full rounded-xl border border-stone-300 bg-white/70 px-3 text-sm outline-none transition focus:border-stone-700 focus:ring-2 focus:ring-stone-500/10 dark:border-stone-700 dark:bg-stone-950 dark:focus:border-stone-400" />
    </label>
  )
}

function TextArea({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="block">
      <FieldLabel>{label}</FieldLabel>
      <textarea value={value} onChange={(event) => onChange(event.target.value)} placeholder="Optional context or outcome" className="mt-1.5 min-h-20 w-full resize-none rounded-xl border border-stone-300 bg-white/70 px-3 py-3 text-sm outline-none transition focus:border-stone-700 focus:ring-2 focus:ring-stone-500/10 dark:border-stone-700 dark:bg-stone-950 dark:focus:border-stone-400" />
    </label>
  )
}

function SelectField({ label, value, options, onChange }: { label?: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return (
    <label className="block min-w-0">
      {label && <FieldLabel>{label}</FieldLabel>}
      <select value={value} onChange={(event) => onChange(event.target.value)} className={`${label ? 'mt-1.5' : ''} h-12 w-full rounded-xl border border-stone-300 bg-white/70 px-3 text-sm outline-none focus:border-stone-700 dark:border-stone-700 dark:bg-stone-950`}>
        {options.map((option) => <option key={option}>{option}</option>)}
      </select>
    </label>
  )
}

function Segmented({ label, options, value, onChange }: { label?: string; options: string[]; value: string; onChange: (value: string) => void }) {
  return (
    <div>
      {label && <FieldLabel>{label}</FieldLabel>}
      <div className={`${label ? 'mt-1.5' : ''} grid gap-1 rounded-xl bg-stone-200/70 p-1 dark:bg-stone-800`} style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
        {options.map((option) => (
          <button key={option} type="button" onClick={() => onChange(option)} className={`min-h-9 rounded-lg px-1.5 text-[10px] font-medium transition ${value === option ? 'bg-[#303536] text-white shadow-sm dark:bg-stone-200 dark:text-stone-950' : 'text-stone-500 hover:text-stone-800 dark:text-stone-400 dark:hover:text-stone-100'}`}>
            {option}
          </button>
        ))}
      </div>
    </div>
  )
}

function DeadlineFields({ draft, update, compact = false }: VariantProps & { compact?: boolean }) {
  return (
    <div className="space-y-3">
      <SelectField label={compact ? undefined : 'Deadline'} value={draft.deadlineMode} options={['None', 'Date only', 'Date and time']} onChange={(value) => update('deadlineMode', value as DeadlineMode)} />
      {draft.deadlineMode !== 'None' && (
        <div className="space-y-2">
          <div className="flex gap-2">
            {['Today', 'Tomorrow', 'Next week'].map((choice) => (
              <button key={choice} type="button" onClick={() => update('deadlineDate', choice)} className="min-h-10 flex-1 rounded-xl border border-stone-300 px-2 text-[10px] font-medium text-stone-600 dark:border-stone-700 dark:text-stone-300">{choice}</button>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-2">
            <input aria-label="Deadline date" type="date" value={/^\d{4}-/.test(draft.deadlineDate) ? draft.deadlineDate : ''} onChange={(event) => update('deadlineDate', event.target.value)} className="h-11 min-w-0 rounded-xl border border-stone-300 bg-white/70 px-2 text-xs dark:border-stone-700 dark:bg-stone-950" />
            {draft.deadlineMode === 'Date and time' && <input aria-label="Deadline time" type="time" value={draft.deadlineTime} onChange={(event) => update('deadlineTime', event.target.value)} className="h-11 min-w-0 rounded-xl border border-stone-300 bg-white/70 px-2 text-xs dark:border-stone-700 dark:bg-stone-950" />}
          </div>
        </div>
      )}
    </div>
  )
}

function MoreOptions({ draft, update, flat = false }: VariantProps & { flat?: boolean }) {
  return (
    <section className={flat ? '' : 'overflow-hidden rounded-[20px] border border-stone-300/70 bg-[#fffdf7] dark:border-stone-800 dark:bg-stone-900'}>
      <button type="button" onClick={() => update('moreOpen', !draft.moreOpen)} className={`flex min-h-14 w-full items-center justify-between text-left ${flat ? '' : 'px-4'}`}>
        <span>
          <span className="block text-xs font-semibold uppercase tracking-[0.14em]">More options</span>
          <span className="mt-1 block text-[11px] text-stone-500">Ready to Plan and reminder</span>
        </span>
        <span className={`text-lg transition ${draft.moreOpen ? 'rotate-180' : ''}`}>⌄</span>
      </button>
      {draft.moreOpen && (
        <div className={`space-y-4 border-t border-stone-300/70 py-4 dark:border-stone-700 ${flat ? '' : 'px-4'}`}>
          <ToggleRow checked={draft.readyToPlan} onChange={(value) => update('readyToPlan', value)} label="Add to Ready to Plan" note="Make this task available when planning your day." />
          {draft.deadlineMode !== 'None' && (
            <>
              <ToggleRow checked={draft.reminder} onChange={(value) => update('reminder', value)} label="Reminder" note="Suggested from the deadline; editable before creation." />
              {draft.reminder && (
                <div className="grid grid-cols-2 gap-2 pl-1">
                  <input aria-label="Reminder date" type="date" value={draft.reminderDate} onChange={(event) => update('reminderDate', event.target.value)} className="h-11 min-w-0 rounded-xl border border-stone-300 bg-white/70 px-2 text-xs dark:border-stone-700 dark:bg-stone-950" />
                  <input aria-label="Reminder time" type="time" value={draft.reminderTime} onChange={(event) => update('reminderTime', event.target.value)} className="h-11 min-w-0 rounded-xl border border-stone-300 bg-white/70 px-2 text-xs dark:border-stone-700 dark:bg-stone-950" />
                </div>
              )}
            </>
          )}
        </div>
      )}
    </section>
  )
}

function ToggleRow({ checked, onChange, label, note }: { checked: boolean; onChange: (value: boolean) => void; label: string; note: string }) {
  return (
    <label className="flex min-h-14 cursor-pointer items-center gap-3">
      <span className="flex-1">
        <span className="block text-sm font-medium">{label}</span>
        <span className="mt-0.5 block text-[11px] leading-4 text-stone-500">{note}</span>
      </span>
      <input className="peer sr-only" type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
      <span className="relative h-7 w-12 rounded-full bg-stone-300 transition peer-checked:bg-[#303536] after:absolute after:left-1 after:top-1 after:size-5 after:rounded-full after:bg-white after:transition-transform peer-checked:after:translate-x-5 dark:bg-stone-700 dark:peer-checked:bg-stone-200 dark:peer-checked:after:bg-stone-900" />
    </label>
  )
}

function CreateDock({ title, label = 'Create task' }: { title: string; label?: string }) {
  const valid = title.trim().length > 0
  return (
    <div className="absolute inset-x-0 bottom-0 z-20 border-t border-stone-300/80 bg-[#fbfaf5]/95 p-4 pb-5 backdrop-blur dark:border-stone-800 dark:bg-stone-950/95">
      <button type="button" disabled={!valid} className="h-12 w-full rounded-xl bg-[#303536] text-sm font-semibold text-white shadow-[0_8px_20px_rgba(34,38,39,0.18)] transition enabled:hover:bg-black disabled:cursor-not-allowed disabled:opacity-35 dark:bg-stone-100 dark:text-stone-950">
        {label}
      </button>
    </div>
  )
}

function CanvasTile({ icon, label, value, children }: { icon: string; label: string; value: string; children: React.ReactNode }) {
  return (
    <label className="relative flex min-h-24 cursor-pointer flex-col justify-between overflow-hidden rounded-2xl border border-stone-300/80 bg-stone-50 p-3 dark:border-stone-700 dark:bg-stone-900">
      <span className="material-symbols-outlined text-[20px] text-stone-500">{icon}</span>
      <span>
        <span className="block truncate text-xs font-medium">{value}</span>
        <span className="mt-0.5 block text-[9px] uppercase tracking-wider text-stone-500">{label}</span>
      </span>
      <span className="absolute inset-0 opacity-0 [&_select]:h-full [&_select]:w-full">{children}</span>
    </label>
  )
}

function LedgerHeading({ number, title, note }: { number: string; title: string; note: string }) {
  return (
    <div className="flex gap-3 border-b border-stone-400/40 pb-4">
      <span className="font-headline text-sm text-stone-400">{number}</span>
      <div><h2 className="font-headline text-xl font-medium tracking-[-0.02em]">{title}</h2><p className="mt-1 text-xs leading-5 text-stone-500">{note}</p></div>
    </div>
  )
}

function LedgerRow({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="grid grid-cols-[86px_1fr] items-start gap-3 border-b border-stone-400/30 pb-4"><FieldLabel>{label}</FieldLabel><div>{children}</div></div>
}

function FieldLabel({ children }: { children: React.ReactNode }) {
  return <span className="block text-[10px] font-semibold uppercase tracking-[0.13em] text-stone-500">{children}</span>
}

function MicroLabel({ children }: { children: React.ReactNode }) {
  return <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-stone-500">{children}</p>
}

function StateInspector({ draft, variant }: { draft: Draft; variant: VariantKey }) {
  const summary = useMemo(() => ({
    prototype: variants.find((item) => item.key === variant)?.name,
    canCreate: draft.title.trim().length > 0,
    payload: {
      title: draft.title.trim(), description: draft.description.trim(), project: draft.location,
      taskType: draft.taskType, status: draft.status, deadlineMode: draft.deadlineMode,
      deadlineDate: draft.deadlineDate || null, deadlineTime: draft.deadlineTime || null,
      urgency: draft.urgency, importance: draft.importance, readyToPlan: draft.readyToPlan,
      reminder: draft.reminder ? { date: draft.reminderDate || null, time: draft.reminderTime || null } : null,
    },
  }), [draft, variant])

  return (
    <aside className="w-full max-w-[430px] rounded-3xl border border-black/10 bg-[#f4f1e8] p-5 shadow-sm dark:border-white/10 dark:bg-stone-900 lg:sticky lg:top-10">
      <div className="flex items-center justify-between">
        <div><p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-stone-500">Visible prototype state</p><h2 className="mt-1 font-headline text-xl font-medium">Variant {variant}</h2></div>
        <button type="button" onClick={() => navigator.clipboard?.writeText(JSON.stringify(summary, null, 2))} className="min-h-10 rounded-full border border-stone-400/50 px-3 text-xs">Copy JSON</button>
      </div>
      <pre className="mt-4 max-h-[620px] overflow-auto whitespace-pre-wrap rounded-2xl bg-[#232726] p-4 text-[11px] leading-5 text-stone-200">{JSON.stringify(summary, null, 2)}</pre>
    </aside>
  )
}

function PrototypeSwitcher({ variant, dark, onToggleDark }: { variant: VariantKey; dark: boolean; onToggleDark: () => void }) {
  const [params, setParams] = useSearchParams()
  const index = variants.findIndex((item) => item.key === variant)

  const cycle = (direction: -1 | 1) => {
    const next = variants[(index + direction + variants.length) % variants.length]
    const nextParams = new URLSearchParams(params)
    nextParams.set('prototype', 'create-task')
    nextParams.set('variant', next.key)
    setParams(nextParams, { replace: true })
  }

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      if (target?.matches('input, textarea, select, [contenteditable]')) return
      if (event.key === 'ArrowLeft') cycle(-1)
      if (event.key === 'ArrowRight') cycle(1)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  })

  if (!import.meta.env.DEV) return null
  const current = variants[index]
  return (
    <nav className="fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-1 rounded-full border border-white/15 bg-[#181b1a] p-1.5 text-white shadow-[0_18px_50px_rgba(0,0,0,0.35)]" aria-label="Prototype variants">
      <button type="button" onClick={() => cycle(-1)} className="grid size-10 place-items-center rounded-full hover:bg-white/10" aria-label="Previous variant">←</button>
      <span className="min-w-[176px] px-3 text-center text-xs font-medium">{current.key} — {current.name}</span>
      <button type="button" onClick={() => cycle(1)} className="grid size-10 place-items-center rounded-full hover:bg-white/10" aria-label="Next variant">→</button>
      <button type="button" onClick={onToggleDark} className="grid size-10 place-items-center rounded-full border-l border-white/10 text-lg hover:bg-white/10" aria-label="Toggle prototype theme">{dark ? '☀' : '◐'}</button>
    </nav>
  )
}
