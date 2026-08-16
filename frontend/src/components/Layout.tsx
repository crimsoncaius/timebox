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
      ? "flex items-center gap-3 border-l-4 border-primary py-2 pl-4 font-medium text-on-surface transition-colors dark:border-dark-on-surface dark:text-dark-on-surface"
      : "flex items-center gap-3 py-2 pl-5 text-on-surface-variant transition-colors hover:bg-surface-container-low dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container";

  return (
    <div className="min-h-screen bg-surface font-body text-on-surface selection:bg-primary-container selection:text-on-primary-container dark:bg-dark-background dark:text-dark-on-surface">
      <aside className="fixed left-0 top-0 z-60 hidden h-screen w-64 flex-col border-r-0 bg-surface px-8 py-8 dark:bg-dark-background lg:flex">
        <div className="mb-12">
          <h1 className="font-headline text-lg font-medium uppercase tracking-widest text-on-surface dark:text-dark-on-surface">
            Timebox
          </h1>
          <p className="mt-1 font-headline text-xs font-light tracking-tight text-on-surface-variant dark:text-dark-on-surface-variant">
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
            to="/battle-plan"
            aria-label="Battle Plan"
            className={() => navItem(location.pathname.startsWith("/battle-plan"))}
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden>
              view_kanban
            </span>
            <span className="font-headline font-light tracking-tight">
              Battle Plan
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
              <span className="text-sm font-medium text-on-surface dark:text-dark-on-surface">
                You
              </span>
              <span className="text-[10px] uppercase tracking-widest text-on-surface-variant/70 dark:text-dark-on-surface-variant">
                Cloud
              </span>
            </div>
          </div>
        </div>
      </aside>

      <div className="min-h-screen pb-20 lg:ml-64 lg:pb-0">
        <header className="sticky top-0 z-50 flex w-full items-center justify-between bg-surface/85 px-4 py-4 backdrop-blur-[24px] dark:bg-dark-background/85 sm:px-8 lg:px-12 lg:py-6">
          <div>
            <h2 className="font-headline text-xl font-light tracking-tighter text-on-surface dark:text-dark-on-surface">
              Timebox
            </h2>
          </div>
          <div className="flex items-center gap-3 text-on-surface dark:text-dark-on-surface">
            <NavLink
              to="/settings"
              aria-label="Settings"
              className={({ isActive }) =>
                [
                  "flex items-center gap-2 rounded-full px-3 py-2 text-sm font-headline font-light tracking-tight transition-colors",
                  isActive
                    ? "bg-surface-container-low text-on-surface dark:bg-dark-surface-container dark:text-dark-on-surface"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container dark:hover:text-dark-on-surface",
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
          className={mainClassName ?? "w-full max-w-7xl px-12 py-12"}
        >
          {children}
        </main>
      </div>
      <nav className="fixed inset-x-0 bottom-0 z-70 grid grid-cols-4 border-t border-outline-variant/20 bg-surface/95 px-2 py-2 backdrop-blur-xl dark:border-dark-outline-variant dark:bg-dark-background/95 lg:hidden">
        <MobileNavLink to={todayHref} label="Day" icon="calendar_today" active={isTodayPath(location.pathname, today)} />
        <MobileNavLink to="/history" label="Chronicle" icon="history" active={location.pathname === "/history"} />
        <MobileNavLink to="/battle-plan" label="Battle Plan" icon="view_kanban" active={location.pathname.startsWith("/battle-plan")} />
        <MobileNavLink to="/task-types" label="Task types" icon="category" active={location.pathname === "/task-types"} />
      </nav>
    </div>
  );
}

function MobileNavLink({ to, label, icon, active }: { to: string; label: string; icon: string; active: boolean }) {
  return (
    <NavLink to={to} aria-label={`${label} mobile navigation`} className={`flex flex-col items-center gap-0.5 rounded-xl px-1 py-1 text-[10px] ${active ? "bg-surface-container-low text-on-surface dark:bg-dark-surface-container dark:text-dark-on-surface" : "text-on-surface-variant dark:text-dark-on-surface-variant"}`}>
      <span className="material-symbols-outlined text-[20px]" aria-hidden>{icon}</span>
      <span className="truncate">{label}</span>
    </NavLink>
  )
}
