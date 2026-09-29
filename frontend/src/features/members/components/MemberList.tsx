import { ShieldCheck } from 'lucide-react'

import type { Member } from '../../../api/members'
import { Avatar } from '../../../components/ui/Avatar'
import { SkeletonFrame } from '../../../components/ui/States'
import { ROLE_LABELS } from '../../auth/userDisplay'

interface MemberListProps {
  members: Member[]
  /** The signed-in user's id: their row says "You". */
  currentUserId: string
}

/**
 * The workspace's members in the backend's order: who they are, their
 * email and their role, as text (an Admin also gets a shield icon, so the
 * role never rests on colour). Read-only - V1 has no member management
 * beyond adding one.
 */
export function MemberList({ members, currentUserId }: MemberListProps) {
  return (
    <ul
      aria-label="Workspace members"
      className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface shadow-xs"
    >
      {members.map((member) => (
        <li key={member.id} className="flex min-h-16 items-center gap-3 px-4 py-3">
          <Avatar name={member.name} seed={member.id} size="md" />
          <div className="min-w-0 flex-1">
            <p className="flex min-w-0 items-center gap-2 text-sm font-medium text-ink">
              <span className="truncate" title={member.name}>
                {member.name}
              </span>
              {member.id === currentUserId && (
                <span className="shrink-0 rounded-full bg-canvas-strong px-2 text-[11px] leading-[18px] font-medium text-ink-muted">
                  You
                </span>
              )}
            </p>
            <p className="truncate text-sm text-ink-muted" title={member.email}>
              {member.email}
            </p>
          </div>
          <span
            className={`inline-flex h-6 shrink-0 items-center gap-1 rounded-full px-2.5 text-xs font-medium ${
              member.role === 'ADMIN' ? 'bg-accent-subtle text-accent' : 'bg-canvas-strong text-ink-muted'
            }`}
          >
            {member.role === 'ADMIN' && <ShieldCheck aria-hidden="true" className="size-3.5" strokeWidth={2} />}
            <span className="sr-only">Role: </span>
            {ROLE_LABELS[member.role]}
          </span>
        </li>
      ))}
    </ul>
  )
}

/** The list's shape while the members load: a few rows as tall as the real ones. */
export function MembersSkeleton() {
  return (
    <SkeletonFrame label="Loading members…">
      <div className="divide-y divide-line rounded-lg border border-line bg-surface shadow-xs">
        {[40, 56, 48].map((width) => (
          <div key={width} className="flex min-h-16 items-center gap-3 px-4 py-3">
            <div className="skeleton size-8 shrink-0 rounded-full!" />
            <div className="flex flex-1 flex-col gap-2">
              <div className="skeleton h-3" style={{ width: `${width}%` }} />
              <div className="skeleton h-3" style={{ width: `${width - 12}%` }} />
            </div>
            <div className="skeleton h-6 w-16 shrink-0 rounded-full!" />
          </div>
        ))}
      </div>
    </SkeletonFrame>
  )
}
