import type { LoadFailure } from '../../api/errors'
import { Alert } from './Alert'
import { Button } from './Button'

interface LoadErrorProps {
  /** What failed, as a sentence: "The dashboard could not be loaded." */
  message: string
  reason: LoadFailure
  onRetry: () => void
}

/** A page whose data could not be loaded: what failed, why in plain words, and Retry. */
export function LoadError({ message, reason, onRetry }: LoadErrorProps) {
  return (
    <div className="flex max-w-lg flex-col items-start gap-3">
      <Alert tone="error">
        {message}{' '}
        {reason === 'network' ? 'The server could not be reached.' : 'The server ran into a problem.'} Please try
        again.
      </Alert>
      <Button variant="secondary" onClick={onRetry}>
        Retry
      </Button>
    </div>
  )
}
