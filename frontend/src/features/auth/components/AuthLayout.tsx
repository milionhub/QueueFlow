import type { ReactNode } from 'react'

import { QueueFlowLogo } from '../../../components/QueueFlowMark'

interface AuthLayoutProps {
  title: string
  description: ReactNode
  children: ReactNode
  /** Below the card, e.g. the link to the other auth screen. */
  footer: ReactNode
}

/** The frame of the sign-in and registration screens: brand, one focused card, a way across. */
export function AuthLayout({ title, description, children, footer }: AuthLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col items-center px-4 py-12 sm:justify-center sm:py-16">
      <main className="w-full max-w-sm">
        <div className="flex justify-center">
          <QueueFlowLogo />
        </div>

        <div className="mt-8 rounded-lg border border-line bg-surface px-5 py-7 shadow-xs sm:px-7 sm:py-8">
          <h1 className="text-xl font-semibold tracking-tight text-ink">{title}</h1>
          <p className="mt-1.5 text-sm leading-6 text-ink-muted">{description}</p>
          <div className="mt-6">{children}</div>
        </div>

        <p className="mt-6 text-center text-sm text-ink-muted">{footer}</p>
      </main>
    </div>
  )
}
