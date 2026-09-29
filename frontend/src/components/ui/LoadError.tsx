import type { LoadFailure } from '../../api/errors'
import { ErrorState } from './States'

interface LoadErrorProps {
  /** What failed, as a sentence: "The dashboard could not be loaded." */
  message: string
  reason: LoadFailure
  onRetry: () => void
  /** `inline` for a section inside a page (comments, activity). */
  size?: 'page' | 'inline'
}

/** A page (or section) whose data could not be loaded: what failed, why in plain words, and Retry. */
export function LoadError(props: LoadErrorProps) {
  return <ErrorState {...props} />
}
