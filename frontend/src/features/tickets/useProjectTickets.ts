import { listProjectTickets, type Ticket } from '../../api/tickets'
import { useResource, type Resource } from '../../lib/useResource'
import { useAuth } from '../auth/useAuth'

/** Every ticket of the project, in the backend's order (ticket number). */
export function useProjectTickets(projectId: string): Resource<Ticket[]> {
  const { authorizedRequest } = useAuth()
  return useResource(`tickets:${projectId}`, (signal) => listProjectTickets(authorizedRequest, projectId, signal))
}
