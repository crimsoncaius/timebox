import { Link } from "react-router-dom";
import type { DayListItem } from "../../lib/api";
import { buildMonthGridUTC, formatMonthYearUTC } from "./historyCalendar";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] as const;

function windowLabel(item: DayListItem): string {
  const end = item.end_hour === 24 ? "24" : `${item.end_hour}:00`;
  return `${item.start_hour}:00–${end}${item.show_full_day ? " · full" : ""}`;
}

type ChronicleMonthGridProps = {
  year: number;
  month: number;
  byDate: Map<string, DayListItem>;
  onPrevMonth: () => void;
  onNextMonth: () => void;
  onThisMonth: () => void;
};

export function ChronicleMonthGrid({
  year,
  month,
  byDate,
  onPrevMonth,
  onNextMonth,
  onThisMonth,
}: ChronicleMonthGridProps) {
  const cells = buildMonthGridUTC(year, month);
  const title = formatMonthYearUTC(year, month);

  return (
    <div data-testid="chronicle-calendar">
      <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <h2
          className="font-headline text-2xl font-light tracking-tight text-on-surface"
          data-testid="chronicle-month-heading"
        >
          {title}
        </h2>
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={onPrevMonth}
            className="rounded-xl bg-surface-container-low px-4 py-2 font-headline text-sm font-light tracking-tight text-on-surface transition-colors hover:bg-surface-container-high"
            aria-label="Previous month"
          >
            <span
              className="material-symbols-outlined align-middle text-lg"
              aria-hidden
            >
              chevron_left
            </span>
          </button>
          <button
            type="button"
            onClick={onThisMonth}
            className="rounded-xl bg-surface-container-low px-4 py-2 font-headline text-sm font-light tracking-tight text-on-surface transition-colors hover:bg-surface-container-high"
          >
            This month
          </button>
          <button
            type="button"
            onClick={onNextMonth}
            className="rounded-xl bg-surface-container-low px-4 py-2 font-headline text-sm font-light tracking-tight text-on-surface transition-colors hover:bg-surface-container-high"
            aria-label="Next month"
          >
            <span
              className="material-symbols-outlined align-middle text-lg"
              aria-hidden
            >
              chevron_right
            </span>
          </button>
        </div>
      </div>

      <div
        className="mb-2 grid grid-cols-7 gap-2 font-label text-xs uppercase tracking-widest text-on-surface-variant"
        aria-hidden
      >
        {WEEKDAYS.map((d) => (
          <div key={d} className="px-2 py-1 text-center">
            {d}
          </div>
        ))}
      </div>

      <div
        className="grid grid-cols-7 gap-2"
        data-testid="chronicle-month-grid"
      >
        {cells.map((cell) => {
          const item = byDate.get(cell.iso);
          const hasArchive = item != null;
          const muted = !cell.inMonth;
          const baseCell =
            "flex min-h-[5.5rem] flex-col rounded-xl p-3 transition-colors md:min-h-[6rem] " +
            (muted
              ? "bg-surface/80 text-on-surface-variant/50 "
              : hasArchive
                ? "bg-surface-container-low text-on-surface hover:bg-surface-container-high "
                : "bg-surface-container-low/50 text-on-surface-variant hover:bg-surface-container-low ");

          const label = hasArchive
            ? `${cell.iso}, archived day, window ${windowLabel(item!)}`
            : `${cell.iso}, open day`;

          return (
            <Link
              key={cell.iso}
              to={`/day/${cell.iso}`}
              className={`group ${baseCell}`}
              data-testid={`chronicle-day-${cell.iso}`}
              aria-label={label}
            >
              <span
                className={`font-headline text-lg font-light tabular-nums ${
                  muted ? "opacity-60" : hasArchive ? "" : "opacity-90"
                }`}
              >
                {cell.dayOfMonth}
              </span>
              {hasArchive && (
                <span className="mt-auto pt-2 font-label text-[10px] uppercase leading-snug tracking-wider text-on-surface-variant/90">
                  {windowLabel(item!)}
                </span>
              )}
            </Link>
          );
        })}
      </div>
    </div>
  );
}
