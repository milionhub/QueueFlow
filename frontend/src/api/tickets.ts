/** The backend's TicketStatus, in workflow order. */
export type TicketStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE'

/** The backend's TicketPriority. */
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
