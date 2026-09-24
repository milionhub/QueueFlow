import { Menu } from 'lucide-react'
import type { Ref } from 'react'

interface AppHeaderProps {
  title: string
  navigationOpen: boolean
  onOpenNavigation: () => void
  menuButtonRef: Ref<HTMLButtonElement>
  navigationId: string
}

/** The bar above every application page: the page title, and the navigation button on small screens. */
export function AppHeader({ title, navigationOpen, onOpenNavigation, menuButtonRef, navigationId }: AppHeaderProps) {
  return (
    <header className="sticky top-0 z-10 flex h-14 shrink-0 items-center gap-3 border-b border-line bg-surface/95 px-4 backdrop-blur-sm sm:px-6">
      <button
        ref={menuButtonRef}
        type="button"
        onClick={onOpenNavigation}
        aria-label="Open navigation"
        aria-expanded={navigationOpen}
        aria-controls={navigationId}
        className="-ml-1.5 inline-flex size-8 items-center justify-center rounded-md text-ink-muted hover:bg-canvas hover:text-ink lg:hidden"
      >
        <Menu aria-hidden="true" className="size-5" strokeWidth={2} />
      </button>
      <h1 className="truncate text-[15px] font-semibold tracking-tight text-ink">{title}</h1>
    </header>
  )
}
