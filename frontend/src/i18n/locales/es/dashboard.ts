import type { dashboard as en } from '../en/dashboard'
import type { Messages } from '../types'

export const dashboard: Messages<typeof en> = {
  title: 'Panel',
  projectCount_one: '{{count}} proyecto',
  projectCount_other: '{{count}} proyectos',
  openTicketCount_one: '{{count}} ticket abierto',
  openTicketCount_other: '{{count}} tickets abiertos',
  loading: 'Cargando el panel…',
  loadError: 'No se ha podido cargar el panel.',
  noTickets: {
    title: 'Todavía no hay tickets',
    body: 'Aquí aparecerán los tickets que se creen en cualquier proyecto.',
  },
  noProjects: {
    title: 'Todavía no hay proyectos',
    adminNext: 'Crea tu primer proyecto en Proyectos.',
    memberNext: 'Un admin del espacio de trabajo puede crear el primer proyecto.',
    goToProjects: 'Ir a Proyectos',
  },
  assigned: {
    title: 'Asignados a ti',
    openCount_one: '{{count}} abierto',
    openCount_other: '{{count}} abiertos',
    showing: 'Mostrando {{shown}} de {{total}}',
    caughtUp: 'Lo tienes todo al día',
    noOpenAssigned: 'No tienes tickets abiertos asignados.',
    nothingAssigned: 'No tienes nada asignado.',
    unassignedOpen_one: '{{count}} ticket abierto está sin asignar.',
    unassignedOpen_other: '{{count}} tickets abiertos están sin asignar.',
  },
  recent: {
    title: 'Actualizados recientemente',
  },
  projects: {
    title: 'Proyectos',
    viewAll: 'Ver todos',
    viewAllHidden: ' los proyectos',
    noTickets: 'Sin tickets',
    open_one: 'abierto',
    open_other: 'abiertos',
    done: '{{done}}/{{total}} hechos',
  },
  status: {
    title: 'Tickets por estado',
    total_one: '{{count}} en total',
    total_other: '{{count}} en total',
    unassigned_one: '{{count}} ticket abierto sin asignar',
    unassigned_other: '{{count}} tickets abiertos sin asignar',
  },
}
