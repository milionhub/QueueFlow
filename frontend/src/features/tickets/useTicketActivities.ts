import { listTicketActivities, type Activity } from '../../api/activities'
import { useResource, type Resource } from '../../lib/useResource'
import { useAuth } from '../auth/useAuth'

/**
 * The ticket's history, oldest first as the backend orders it. Call
 * `reload` after a change of the ticket: the backend records the entries,
 * the page never makes them up.
 */
export function useTicketActivities(ticketId: string): Resource<Activity[]> {
  const { authorizedRequest } = useAuth()
  return useResource(`activities:${ticketId}`, (signal) => listTicketActivities(authorizedRequest, ticketId, signal))
}
