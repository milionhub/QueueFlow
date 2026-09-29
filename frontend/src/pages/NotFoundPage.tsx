import { Compass } from 'lucide-react'
import { Link } from 'react-router'

import { buttonLinkClasses } from '../components/ui/buttonStyles'
import { EmptyState } from '../components/ui/States'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <EmptyState
      as="h1"
      icon={Compass}
      title="Page not found"
      className="max-w-xl"
      action={
        <Link to="/" className={buttonLinkClasses('secondary')}>
          Go to QueueFlow
        </Link>
      }
    >
      The page you are looking for does not exist.
    </EmptyState>
  )
}
