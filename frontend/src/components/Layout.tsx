import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { api } from "../lib/api";
import { ThemeToggle } from "./ThemeToggle";

function isTodayPath(pathname: string, today: string | null) {
  if (!today) return pathname === "/";
  return pathname === "/" || pathname === `/day/${today}`;
}

export function Layout({
  children,
  mainClassName,
}: {
  children: ReactNode;
  /** Optional extra classes for the main content area. */
  mainClassName?: string;
}) {
  const location = useLocation();
  const [today, setToday] = useState<string | null>(null);

  useEffect(() => {
    void api
      .health()
      .then((h) => setToday(h.today))
      .catch(() => setToday(null));
  }, []);

  const todayHref = today ? `/day/${today}` : "/";

  const navItem = (active: boolean) =>
    active
      ? "flex items-center gap-3 border-l-4 border-stone-800 py-2 pl-4 font-medium text-stone-900 transition-colors dark:border-stone-200 dark:text-stone-50"
      : "flex items-center gap-3 py-2 pl-5 text-stone-500 transition-colors hover:bg-stone-200/50 dark:text-stone-400 dark:hover:bg-stone-800/50";

  return (
    <div className="min-h-screen bg-surface font-body text-on-surface selection:bg-primary-container selection:text-on-primary-container dark:bg-stone-950 dark:text-stone-100">
      <aside className="fixed left-0 top-0 z-[60] flex h-screen w-64 flex-col border-r-0 bg-[#f9f9f9] px-6 py-8 dark:bg-stone-950">
        <div className="mb-12">
          <h1 className="font-headline text-lg font-semibold uppercase tracking-widest text-stone-900 dark:text-stone-100">
            Timebox
          </h1>
          <p className="mt-1 font-headline text-xs font-light tracking-tight text-stone-500 dark:text-stone-400">
            Monastic productivity
          </p>
        </div>
        <nav className="flex flex-1 flex-col gap-2">
          <NavLink
            to={todayHref}
            aria-label="Day"
            className={() => navItem(isTodayPath(location.pathname, today))}
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden>
              calendar_today
            </span>
            <span className="font-headline font-light tracking-tight">Day</span>
          </NavLink>
          <NavLink
            to="/history"
            aria-label="Chronicle"
            className={() => navItem(location.pathname === "/history")}
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden>
              history
            </span>
            <span className="font-headline font-light tracking-tight">
              Chronicle
            </span>
          </NavLink>
          <NavLink
            to="/task-types"
            aria-label="Task types"
            className={() => navItem(location.pathname === "/task-types")}
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden>
              category
            </span>
            <span className="font-headline font-light tracking-tight">
              Task types
            </span>
          </NavLink>
        </nav>
        <div className="mt-auto">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-container font-headline text-xs font-semibold text-on-primary-container">
              TB
            </div>
            <div className="flex flex-col">
              <span className="text-sm font-medium text-stone-900 dark:text-stone-100">
                You
              </span>
              <span className="text-[10px] uppercase tracking-widest text-stone-400">
                Local
              </span>
            </div>
          </div>
        </div>
      </aside>

      <div className="ml-64 min-h-screen">
        <header className="sticky top-0 z-50 flex w-full items-center justify-between bg-[#f9f9f9]/85 px-12 py-6 shadow-sm backdrop-blur-xl dark:bg-stone-950/85 dark:shadow-none">
          <div>
            <h2 className="font-headline text-xl font-light tracking-tighter text-stone-900 dark:text-white">
              Timebox
            </h2>
          </div>
          <div className="flex items-center gap-3 text-stone-800 dark:text-stone-200">
            <NavLink
              to="/settings"
              aria-label="Settings"
              className={({ isActive }) =>
                [
                  "flex items-center gap-2 rounded-full px-3 py-2 text-sm font-headline font-light tracking-tight transition-colors",
                  isActive
                    ? "bg-stone-200/80 text-stone-900 dark:bg-stone-800/80 dark:text-stone-50"
                    : "text-stone-600 hover:bg-stone-200/60 hover:text-stone-900 dark:text-stone-400 dark:hover:bg-stone-800/60 dark:hover:text-stone-100",
                ].join(" ")
              }
            >
              <span
                className="material-symbols-outlined text-[20px]"
                aria-hidden
              >
                settings
              </span>
              <span>Settings</span>
            </NavLink>
            <ThemeToggle />
          </div>
        </header>
        <main
          className={mainClassName ?? "mx-auto w-full max-w-7xl px-12 py-12"}
        >
          {children}
        </main>
      </div>
    </div>
  );
}
