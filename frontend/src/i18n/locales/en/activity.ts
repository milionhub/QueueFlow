/**
 * A ticket's history. Each entry is one whole sentence with placeholders,
 * so every language can put the actor, values and names where its grammar
 * wants them; names, labels and titles are data and are never translated.
 */
export const activity = {
  title: 'Activity',
  loading: 'Loading activity…',
  loadError: 'The activity could not be loaded.',
  empty: 'No activity yet.',
  stale: 'The activity could not be updated, so it may be out of date.',
  showRecent: 'Show only recent events',
  showEarlier_one: 'Show {{count}} earlier event',
  showEarlier_other: 'Show {{count}} earlier events',
  none: 'none',
  entries: {
    created: '{{actor}} created the ticket',
    renamedTo: '{{actor}} renamed the ticket to “{{title}}”',
    renamed: '{{actor}} renamed the ticket',
    descriptionAdded: '{{actor}} added a description',
    descriptionRemoved: '{{actor}} removed the description',
    descriptionUpdated: '{{actor}} updated the description',
    statusChanged: '{{actor}} moved the ticket from {{from}} to {{to}}',
    priorityChanged: '{{actor}} changed priority from {{from}} to {{to}}',
    assignedSelf: '{{actor}} assigned the ticket to themselves',
    assigned: '{{actor}} assigned the ticket to {{person}}',
    unassignedSelf: '{{actor}} unassigned themselves',
    unassigned: '{{actor}} unassigned {{person}}',
    reassigned: '{{actor}} reassigned the ticket from {{from}} to {{to}}',
    assigneeChanged: '{{actor}} changed the assignee',
    labelAdded: '{{actor}} added label {{label}}',
    labelAddedUnknown: '{{actor}} added a label',
    labelRemoved: '{{actor}} removed label {{label}}',
    labelRemovedUnknown: '{{actor}} removed a label',
    other: '{{actor}} updated the ticket',
  },
}
