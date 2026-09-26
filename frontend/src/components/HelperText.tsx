import type { HTMLAttributes, ReactNode } from 'react'

/** Muted supporting copy under a control: hints, notices, empty states and inline problems. */
export function HelperText({ children, error = false, className = '', ...rest }: { children: ReactNode; error?: boolean } & HTMLAttributes<HTMLParagraphElement>) {
  const tone = error ? 'text-error' : 'text-on-surface-variant dark:text-dark-on-surface-variant'
  return <p className={`text-xs leading-5 ${tone} ${className}`.trim()} {...rest}>{children}</p>
}

/** Saved raw data shown for review: legible, bounded, and visibly secondary to the notice it supports. */
export function DiagnosticText({ children }: { children: ReactNode }) {
  return <pre className="mt-2 max-h-44 overflow-auto whitespace-pre-wrap break-all rounded-xl bg-surface-container-low p-3 font-mono text-[11px] leading-4 text-on-surface-variant dark:bg-dark-surface-container dark:text-dark-on-surface-variant">{children}</pre>
}
