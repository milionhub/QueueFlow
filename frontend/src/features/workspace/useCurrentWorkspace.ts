import { useContext } from 'react'

import { CurrentWorkspaceContext, type CurrentWorkspace } from './currentWorkspaceContext'

export function useCurrentWorkspace(): CurrentWorkspace {
  const context = useContext(CurrentWorkspaceContext)
  if (!context) {
    throw new Error('useCurrentWorkspace must be used inside <CurrentWorkspaceProvider>')
  }
  return context
}
