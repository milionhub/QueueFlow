import type { shell as en } from '../en/shell'
import type { Messages } from '../types'

export const shell: Messages<typeof en> = {
  nav: {
    main: 'Principal',
    dashboard: 'Panel',
    projects: 'Proyectos',
    members: 'Miembros',
  },
  sidebar: 'Barra lateral',
  drawer: 'Navegación',
  openNavigation: 'Abrir navegación',
  closeNavigation: 'Cerrar navegación',
  breadcrumb: 'Ruta de navegación',
  back: ' (volver)',
  board: 'Tablero',
  account: {
    menu: 'Cuenta',
    triggerSuffix: ', menú de la cuenta',
    signOut: 'Cerrar sesión',
    language: 'Idioma',
  },
  titles: {
    dashboard: 'Panel',
    projects: 'Proyectos',
    members: 'Miembros',
    notFound: 'Página no encontrada',
    boardDocument: 'Tablero de {{key}}',
  },
}
