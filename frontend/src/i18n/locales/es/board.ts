import type { board as en } from '../en/board'
import type { Messages } from '../types'

export const board: Messages<typeof en> = {
  emptyBody:
    'El tablero muestra los tickets de este proyecto en una columna por estado. Cada ticket recibe una clave como <key>{{key}}-1</key>.',
  stale: 'No se ha podido actualizar el tablero y puede que no esté al día.',
  noMatch: 'Ningún ticket coincide con estos filtros.',
  moved: '{{key}} movido a {{status}}.',
  moveGone: '{{key}} ya no está disponible.',
  moveFailed: 'No se ha podido mover {{key}} a {{status}}: {{reason}}',
  columnsNav: 'Columnas del tablero',
  column: {
    dropHint: 'Suelta para mover a {{status}}',
    countOf: '{{count}} de {{total}}',
    count_one: ', {{count}} ticket',
    count_other: ', {{count}} tickets',
    filteredCount_one: ', se muestran {{shown}} de {{count}} ticket',
    filteredCount_other: ', se muestran {{shown}} de {{count}} tickets',
    noMatching: 'Ningún ticket coincide',
    empty: 'Sin tickets',
  },
  card: {
    drag: 'Arrastrar {{key}}',
  },
  move: {
    trigger: 'Mover {{key}}, ahora en {{status}}',
    tooltip: 'Mover a otro estado',
    group: 'Mover {{key}} a',
    heading: 'Mover a…',
  },
  dnd: {
    instructions:
      'Para mover este ticket a otro estado, pulsa Espacio o Intro para cogerlo, usa las flechas izquierda y derecha para elegir una columna y pulsa Espacio o Intro para soltarlo ahí. Pulsa Escape para cancelar.',
    pickedUp: 'Has cogido {{key}}, ahora en {{status}}.',
    notOverColumn: '{{key}} no está sobre ninguna columna.',
    overCurrent: '{{key}} está sobre su columna actual, {{status}}.',
    over: '{{key}} está sobre {{status}}.',
    cancelled: 'Cancelado. {{key}} se queda en {{status}}.',
    droppedOutside: 'Has soltado {{key}} fuera de las columnas. Se queda en {{status}}.',
    droppedSame: 'Has soltado {{key}} en su columna actual. No ha cambiado nada.',
    dropped: 'Has soltado {{key}}. Moviendo a {{status}}.',
  },
}
