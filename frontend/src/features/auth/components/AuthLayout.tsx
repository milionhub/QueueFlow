import { Columns3, History, MessageSquare } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import type { TicketPriority, TicketStatus } from '../../../api/tickets'
import { LanguageSwitcher } from '../../../components/LanguageSwitcher'
import { QueueFlowLogo } from '../../../components/QueueFlowMark'
import { PriorityIcon, StatusIcon } from '../../tickets/TicketBadges'
import { STATUS_TONE } from '../../tickets/ticketDisplay'

interface AuthLayoutProps {
  title: string
  description: ReactNode
  children: ReactNode
  /** Below the form, e.g. the link to the other auth screen. */
  footer: ReactNode
}

/**
 * The entrance to QueueFlow, shared by sign-in and registration. From `lg`
 * a split composition: the brand panel (the product's promise and a small
 * picture of work moving across a board) and the form on a calm white
 * side. Below `lg` one column: the brand, the heading and the form - the
 * workflow appears only as a thin strip of the five status colours, so the
 * form stays on the first screen of a phone. Under the footer, a quiet
 * switch of the interface language (there is no account menu yet).
 */
export function AuthLayout({ title, description, children, footer }: AuthLayoutProps) {
  const { t } = useTranslation('auth')
  return (
    <div className="min-h-dvh bg-canvas lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
      <BrandPanel />

      <div className="relative flex min-h-dvh flex-col px-5 pt-[max(2rem,env(safe-area-inset-top))] pb-10 sm:items-center sm:justify-center sm:px-8 sm:py-16 lg:bg-surface">
        <WorkflowStrip className="absolute inset-x-0 top-0 lg:hidden" />
        <main className="w-full max-w-sm animate-enter">
          <div className="flex items-center justify-between gap-3 lg:hidden">
            <QueueFlowLogo size="md" />
            <span className="min-w-0 text-right text-xs font-medium tracking-wide text-ink-muted">
              {t('brand.tagline')}
            </span>
          </div>

          <div className="mt-8 sm:rounded-xl sm:border sm:border-line sm:bg-surface sm:px-8 sm:py-8 sm:shadow-sm lg:mt-0 lg:rounded-none lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none">
            <h1 className="text-2xl leading-8 font-semibold tracking-tight text-ink">{title}</h1>
            <p className="mt-1.5 text-sm leading-6 text-ink-muted">{description}</p>
            <div className="mt-7">{children}</div>
          </div>

          <p className="mt-6 text-sm text-ink-muted sm:text-center lg:text-left">{footer}</p>
          <LanguageSwitcher className="mt-4 sm:justify-center lg:justify-start" />
        </main>
      </div>
    </div>
  )
}

/** A 3px line of the five status colours, in workflow order. Decorative. */
function WorkflowStrip({ className = '' }: { className?: string }) {
  const order: TicketStatus[] = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE']
  return (
    <div aria-hidden="true" className={`flex h-[3px] ${className}`}>
      {order.map((status) => (
        <span key={status} className={`flex-1 ${STATUS_TONE[status].fill}`} />
      ))}
    </div>
  )
}

type SampleTitle = 'pricing' | 'rateLimit' | 'footer' | 'onboarding' | 'sso' | 'emptyStates' | 'projectKeys'

interface MiniCard {
  key: string
  /** The made-up title: a message in auth.brand.sample. */
  titleKey: SampleTitle
  priority: TicketPriority
  /** The card being moved on: lifted, with the accent ring. */
  moving?: boolean
}

/** A few made-up tickets for the picture; nothing here is real data. */
const MINI_BOARD: { status: TicketStatus; cards: MiniCard[]; wideOnly?: boolean }[] = [
  {
    status: 'BACKLOG',
    wideOnly: true,
    cards: [{ key: 'APP-12', titleKey: 'pricing', priority: 'LOW' }],
  },
  {
    status: 'TODO',
    cards: [
      { key: 'APP-9', titleKey: 'rateLimit', priority: 'HIGH' },
      { key: 'APP-11', titleKey: 'footer', priority: 'LOW' },
    ],
  },
  {
    status: 'IN_PROGRESS',
    cards: [{ key: 'APP-7', titleKey: 'onboarding', priority: 'CRITICAL', moving: true }],
  },
  { status: 'REVIEW', cards: [{ key: 'APP-5', titleKey: 'sso', priority: 'MEDIUM' }] },
  {
    status: 'DONE',
    cards: [
      { key: 'APP-2', titleKey: 'emptyStates', priority: 'MEDIUM' },
      { key: 'APP-1', titleKey: 'projectKeys', priority: 'LOW' },
    ],
  },
]

/**
 * The brand side (from `lg`): deep indigo with a faint dot grid, the
 * product's promise, and a small, static picture of QueueFlow's own board -
 * five status columns and a card moving towards Review.
 */
function BrandPanel() {
  const { t } = useTranslation(['auth', 'tickets'])
  return (
    <aside
      aria-label={t('brand.about')}
      className="auth-dots relative hidden min-h-dvh flex-col justify-between overflow-hidden bg-brand-deep px-12 py-12 text-white lg:flex xl:px-16"
    >
      <QueueFlowLogo size="lg" tone="inverse" />

      <div className="flex flex-col gap-10">
        <div className="max-w-md">
          <p className="text-4xl leading-tight font-semibold tracking-tight text-white xl:text-5xl xl:leading-[1.1]">
            {t('brand.tagline')}
          </p>
          <p className="mt-4 text-base leading-7 text-[#c7cdf5]">
            {t('brand.promise')}
          </p>
        </div>

        <div aria-hidden="true" className="flex animate-enter gap-2.5">
          {MINI_BOARD.map(({ status, cards, wideOnly }) => (
            <div
              key={status}
              className={`min-w-0 flex-1 flex-col gap-2 rounded-lg border border-brand-deep-line bg-white/[0.03] p-2 ${
                wideOnly ? 'hidden xl:flex' : 'flex'
              }`}
            >
              <div className="flex items-center gap-1.5 px-0.5 text-[11px] font-medium text-[#c7cdf5]">
                <StatusIcon status={status} className="size-3" />
                <span className="truncate">{t(`tickets:status.${status}`)}</span>
              </div>
              {cards.map((card) => (
                <div
                  key={card.key}
                  className={`flex flex-col gap-1.5 rounded-md border p-2 ${
                    card.moving
                      ? 'translate-x-1.5 -translate-y-0.5 border-accent bg-brand-deep-raised shadow-lg ring-1 shadow-black/30 ring-accent/60'
                      : 'border-brand-deep-line bg-brand-deep-raised'
                  }`}
                >
                  <span className="font-mono text-[10px] text-[#9aa2de]">{card.key}</span>
                  <span className="text-[11px] leading-4 font-medium text-white/90">{t(`brand.sample.${card.titleKey}`)}</span>
                  <span className="flex items-center justify-between">
                    <PriorityIcon priority={card.priority} className="size-3" inverse />
                    <span className="size-3.5 rounded-full bg-[#3a4190] ring-1 ring-white/10" />
                  </span>
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>

      <ul className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-[#c7cdf5]">
        <li className="flex items-center gap-2">
          <Columns3 aria-hidden="true" className="size-4 text-[#9aa2de]" strokeWidth={2} />
          {t('brand.features.boards')}
        </li>
        <li className="flex items-center gap-2">
          <MessageSquare aria-hidden="true" className="size-4 text-[#9aa2de]" strokeWidth={2} />
          {t('brand.features.conversations')}
        </li>
        <li className="flex items-center gap-2">
          <History aria-hidden="true" className="size-4 text-[#9aa2de]" strokeWidth={2} />
          {t('brand.features.history')}
        </li>
      </ul>
    </aside>
  )
}
