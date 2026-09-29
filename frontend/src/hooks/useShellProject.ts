import { createContext, useContext, useEffect } from 'react'

/** The project a page belongs to, as far as the shell's breadcrumbs need it. */
export interface ShellProject {
  key: string
  name: string
}

export const ShellProjectContext = createContext<((project: ShellProject | null) => void) | null>(null)

/**
 * Tells the shell the loaded project's name, so the breadcrumbs can show
 * it instead of the key from the address. Until then they show the key.
 */
export function useShellProject(project: ShellProject | null) {
  const setProject = useContext(ShellProjectContext)
  const key = project?.key ?? null
  const name = project?.name ?? null
  useEffect(() => {
    if (!setProject || key === null || name === null) {
      return
    }
    setProject({ key, name })
    return () => setProject(null)
  }, [setProject, key, name])
}
