/** A project's board: columns, cards, moving tickets and what drag and drop announces. */
export const board = {
  emptyBody:
    "The board shows this project's tickets in a column for each status. Each ticket gets a key like <key>{{key}}-1</key>.",
  stale: 'The board could not be refreshed and may be out of date.',
  noMatch: 'No tickets match these filters.',
  moved: '{{key}} moved to {{status}}.',
  moveGone: '{{key}} is no longer available.',
  moveFailed: "Couldn't move {{key}} to {{status}}: {{reason}}",
  columnsNav: 'Board columns',
  column: {
    dropHint: 'Drop to move to {{status}}',
    countOf: '{{count}} of {{total}}',
    count_one: ', {{count}} ticket',
    count_other: ', {{count}} tickets',
    filteredCount_one: ', {{shown}} of {{count}} ticket shown',
    filteredCount_other: ', {{shown}} of {{count}} tickets shown',
    noMatching: 'No matching tickets',
    empty: 'No tickets',
  },
  card: {
    drag: 'Drag {{key}}',
  },
  move: {
    trigger: 'Move {{key}}, currently {{status}}',
    tooltip: 'Move to another status',
    group: 'Move {{key}} to',
    heading: 'Move to…',
  },
  dnd: {
    instructions:
      'To move this ticket to another status, press Space or Enter to pick it up, use the Left and Right arrow keys to choose a column, then press Space or Enter to drop it there. Press Escape to cancel.',
    pickedUp: 'Picked up {{key}}, currently in {{status}}.',
    notOverColumn: '{{key}} is not over a column.',
    overCurrent: '{{key}} is over its current column, {{status}}.',
    over: '{{key}} is over {{status}}.',
    cancelled: 'Cancelled. {{key}} stays in {{status}}.',
    droppedOutside: 'Dropped {{key}} outside the columns. It stays in {{status}}.',
    droppedSame: 'Dropped {{key}} in its current column. Nothing changed.',
    dropped: 'Dropped {{key}}. Moving to {{status}}.',
  },
}
