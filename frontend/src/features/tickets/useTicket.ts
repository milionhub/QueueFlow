import { getTicketByNumber, type Ticket } from '../../api/tickets'
import { useResource, type Resource } from '../../lib/useResource'
import { useAuth } from '../auth/useAuth'

/** One ticket of the project by its number; `null` (an invalid number in the address) loads nothing. */
export function useTicket(projectId: string, ticketNumber: number | null): Resource<Ticket> {
  const { authorizedRequest } = useAuth()
  return useResource(ticketNumber === null ? null : `ticket:${projectId}:${ticketNumber}`, (signal) =>
    getTicketByNumber(authorizedRequest, projectId, ticketNumber ?? 0, signal),
  )
}
