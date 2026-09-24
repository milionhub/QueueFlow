import type { ReactNode } from 'react'

interface SectionHeadingProps {
  id: string
  title: string
  /** Muted text after the title, e.g. "· 7 open". */
  detail?: string
  /** Muted text at the far end of the row. */
  children?: ReactNode
}

/** The title row above every dashboard section. */
export function SectionHeading({ id, title, detail, children }: SectionHeadingProps) {
  return (
    <div className="mb-2 flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
      <h2 id={id} className="text-sm font-semibold text-ink">
        {title}
        {detail && <span className="font-normal text-ink-subtle"> · {detail}</span>}
      </h2>
      {children && <p className="text-xs text-ink-muted">{children}</p>}
    </div>
  )
}
