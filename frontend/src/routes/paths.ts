/**
 * The addresses of the signed-in application, built in one place. Project
 * keys are A-Z and 0-9 only and never change, and ticket numbers are
 * positive integers, so both can appear in URLs as they are.
 */

export const PROJECTS_PATH = '/app/projects'

/** A project's page, which is its ticket list: /app/projects/CORE */
export function projectPath(projectKey: string): string {
  return `${PROJECTS_PATH}/${encodeURIComponent(projectKey)}`
}

/** A project's board: /app/projects/CORE/board */
export function projectBoardPath(projectKey: string): string {
  return `${projectPath(projectKey)}/board`
}

/**
 * The navigation state a link from the board passes to a ticket's page, so
 * its back link returns to the board. Kept by refresh; absent on a direct visit.
 */
export const FROM_BOARD = { from: 'board' } as const

export function isFromBoard(state: unknown): boolean {
  return (state as { from?: unknown } | null)?.from === FROM_BOARD.from
}

/** A ticket's page: /app/projects/CORE/tickets/7 */
export function ticketPath(projectKey: string, ticketNumber: number): string {
  return `${projectPath(projectKey)}/tickets/${ticketNumber}`
}

/**
 * A ticket's page from its display key ("CORE-7"). A key never contains
 * "-", so the display key splits at its first one.
 */
export function ticketPathFromDisplayKey(displayKey: string): string {
  const separator = displayKey.indexOf('-')
  return ticketPath(displayKey.slice(0, separator), Number(displayKey.slice(separator + 1)))
}

/**
 * A ticket number from the URL: a positive integer written plainly ("7"),
 * or null for anything else ("0", "-1", "7.5", "07", "abc").
 */
export function parseTicketNumber(value: string | undefined): number | null {
  if (!value || !/^[1-9]\d*$/.test(value)) {
    return null
  }
  const number = Number(value)
  return Number.isSafeInteger(number) ? number : null
}
