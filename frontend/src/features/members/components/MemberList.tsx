import type { Member } from '../../../api/members'
import { initials, ROLE_LABELS } from '../../auth/userDisplay'

const BLOCK = 'rounded bg-line motion-safe:animate-pulse'

const ROLE_BADGE_CLASSES = {
  ADMIN: 'border-accent/20 bg-accent-subtle text-accent',
  MEMBER: 'border-line bg-canvas text-ink-muted',
} as const

interface MemberListProps {
  members: Member[]
  /** The signed-in user's id: their row says "You". */
  currentUserId: string
}

/**
 * The workspace's members in the backend's order: who they are, their
 * email and their role, as text. Read-only - V1 has no member management
 * beyond adding one.
 */
export function MemberList({ members, currentUserId }: MemberListProps) {
  return (
    <ul aria-label="Workspace members" className="divide-y divide-line rounded-md border border-line bg-surface">
      {members.map((member) => (
        <li key={member.id} className="flex items-center gap-3 px-4 py-3">
          <span
            aria-hidden="true"
            className="flex size-8 shrink-0 items-center justify-center rounded-full bg-accent-subtle text-xs font-semibold text-accent"
          >
            {initials(member.name)}
          </span>
          <div className="min-w-0 flex-1">
            <p className="flex min-w-0 items-center gap-2 text-sm font-medium text-ink">
              <span className="truncate" title={member.name}>
                {member.name}
              </span>
              {member.id === currentUserId && (
                <span className="shrink-0 rounded border border-line px-1.5 text-[11px] leading-4 font-medium text-ink-muted">
                  You
                </span>
              )}
            </p>
            <p className="truncate text-sm text-ink-muted" title={member.email}>
              {member.email}
            </p>
          </div>
          <span
            className={`shrink-0 rounded-full border px-2 text-xs leading-5 font-medium ${ROLE_BADGE_CLASSES[member.role]}`}
          >
            <span className="sr-only">Role: </span>
            {ROLE_LABELS[member.role]}
          </span>
        </li>
      ))}
    </ul>
  )
}

/** The page's shape while the members load: context line and a few rows. */
export function MembersSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-4">
      <span className="sr-only" role="status">
        Loading members…
      </span>
      <div aria-hidden="true" className="flex flex-col gap-4">
        <div className={`h-4 w-48 max-w-full ${BLOCK}`} />
        <div className="divide-y divide-line rounded-md border border-line bg-surface">
          {[40, 56, 48].map((width) => (
            <div key={width} className="flex items-center gap-3 px-4 py-3">
              <div className={`size-8 shrink-0 rounded-full ${BLOCK}`} />
              <div className="flex flex-1 flex-col gap-2">
                <div className={`h-3 ${BLOCK}`} style={{ width: `${width}%` }} />
                <div className={`h-3 ${BLOCK}`} style={{ width: `${width - 12}%` }} />
              </div>
              <div className={`h-5 w-14 shrink-0 rounded-full ${BLOCK}`} />
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
