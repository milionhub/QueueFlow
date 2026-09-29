/**
 * The QueueFlow brand. The mark is three stages of work stepping forward
 * and down, from queued (faint) to shipped (solid) - a queue that flows -
 * on the indigo tile. Simple enough to read at 20px; the same drawing is
 * the favicon. Always decorative: the name is next to it or in the page.
 */
export function QueueFlowMark({
  className = 'size-7',
  tone = 'brand',
}: {
  className?: string
  tone?: 'brand' | 'inverse'
}) {
  const tile = tone === 'brand' ? 'var(--color-accent)' : '#fff'
  const bars = tone === 'brand' ? '#fff' : 'var(--color-accent)'
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={`shrink-0 ${className}`}>
      <rect width="32" height="32" rx="8" fill={tile} />
      <rect x="6.5" y="8" width="10" height="4" rx="2" fill={bars} opacity=".5" />
      <rect x="10.5" y="14" width="12" height="4" rx="2" fill={bars} opacity=".78" />
      <rect x="14.5" y="20" width="11" height="4" rx="2" fill={bars} />
    </svg>
  )
}

/** The wordmark: "Queue" in ink, "Flow" in the brand colour. */
export function QueueFlowWordmark({
  className = 'text-[15px]',
  tone = 'brand',
}: {
  className?: string
  tone?: 'brand' | 'inverse'
}) {
  return (
    <span className={`font-semibold tracking-tight ${tone === 'brand' ? 'text-ink' : 'text-white'} ${className}`}>
      Queue<span className={tone === 'brand' ? 'text-accent' : 'text-[#b9c2ff]'}>Flow</span>
    </span>
  )
}

/** Mark plus name. */
export function QueueFlowLogo({
  size = 'md',
  tone = 'brand',
}: {
  size?: 'sm' | 'md' | 'lg'
  tone?: 'brand' | 'inverse'
}) {
  const mark = { sm: 'size-6', md: 'size-7', lg: 'size-9' }[size]
  const text = { sm: 'text-[15px]', md: 'text-lg', lg: 'text-xl' }[size]
  return (
    <span className="inline-flex items-center gap-2.5">
      <QueueFlowMark className={mark} tone={tone} />
      <QueueFlowWordmark className={text} tone={tone} />
    </span>
  )
}
