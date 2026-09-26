import { trendDuration, trendRangeDayCount, trendRangeLabel, type TrendRange } from './trends'

export type Highlight = { name: string; days: Record<string, number>; range: TrendRange }

/** Calendar header while Trends contributing days are highlighted: a breadcrumb back to the range and a one-line summary. */
export function ChronicleHighlight({ highlight, onBack, onClear }: { highlight: Highlight; onBack: () => void; onClear: () => void }) {
  const segments = highlight.name.split('/').filter(Boolean)
  const leaf = segments.at(-1) ?? highlight.name
  const contributing = Object.keys(highlight.days).length
  const rangeDays = trendRangeDayCount(highlight.range)
  const total = Object.values(highlight.days).reduce((sum, seconds) => sum + seconds, 0)
  return (
    <div className="mb-8 flex items-start justify-between gap-6 border-l-2 border-actual pl-4" data-testid="chronicle-highlight">
      <div className="min-w-0">
        <nav className="text-sm text-on-surface-variant" aria-label="Highlight source">
          <button type="button" onClick={onBack} className="underline-offset-4 hover:text-on-surface hover:underline">Trends</button>
          <span className="mx-2" aria-hidden>›</span>{trendRangeLabel(highlight.range)}
        </nav>
        <p className="mt-2 font-headline text-xl font-light tracking-tight text-on-surface">
          {segments.slice(0, -1).map(parent => <span key={parent} className="text-on-surface-variant">{parent} / </span>)}
          <span className="font-normal">{leaf}</span> on{' '}
          <span className="tabular-nums">{contributing} of {rangeDays}</span> {rangeDays === 1 ? 'day' : 'days'},{' '}
          <span className="tabular-nums">{trendDuration(total)}</span> in total.
        </p>
      </div>
      <button type="button" onClick={onClear} className="shrink-0 rounded-full px-3 py-1.5 text-sm text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface">Show all activity</button>
    </div>
  )
}
