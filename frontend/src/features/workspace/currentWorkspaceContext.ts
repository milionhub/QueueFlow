import { createContext } from 'react'

import type { Workspace } from '../../api/workspaces'

export type CurrentWorkspace =
  | { status: 'loading'; workspace: null }
  | { status: 'ready'; workspace: Workspace }
  /** Not fatal: the shell keeps working and shows a neutral fallback. */
  | { status: 'unavailable'; workspace: null }

export const CurrentWorkspaceContext = createContext<CurrentWorkspace | null>(null)
