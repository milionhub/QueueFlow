import { ShieldCheck } from 'lucide-react'
import type { Ref } from 'react'
import { useTranslation } from 'react-i18next'

import type { Member } from '../../../api/members'
import { Avatar } from '../../../components/ui/Avatar'
import { SkeletonFrame } from '../../../components/ui/States'
import { roleLabel } from '../../auth/userDisplay'
import { MemberActionsMenu } from './MemberActionsMenu'

interface MemberListProps {
  ref?: Ref<HTMLUListElement>
  members: Member[]
  /** The signed-in user's id: their row says "You". */
  currentUserId: string
  /**
   * Given only to an ADMIN: every row that can be managed - another
   * MEMBER - gets a ⋯ menu with Edit and Remove. `opener` is that ⋯ button.
   */
  actions?: {
    onEdit: (member: Member, opener: HTMLElement) => void
    onRemove: (member: Member, opener: HTMLElement) => void
  }
}

/** The members an ADMIN can edit and remove: MEMBERs other than themselves (never an ADMIN in V1). */
function isManageable(member: Member, currentUserId: string): boolean {
  return member.role === 'MEMBER' && member.id !== currentUserId
}

/**
 * The workspace's members in the backend's order: who they are, their
 * email and their role, as text (an Admin also gets a shield icon, so the
 * role never rests on colour). For an ADMIN, a quiet ⋯ menu at the end of
 * each manageable row; the other rows keep its space, so the role badges
 * stay in one column.
 *
 * Focusable from script only (tabIndex -1): focus lands on the list when
 * the row it was on has just been removed.
 */
export function MemberList({ ref, members, currentUserId, actions }: MemberListProps) {
  const { t } = useTranslation(['members', 'common'])
  return (
    <ul
      ref={ref}
      tabIndex={-1}
      aria-label={t('list.label')}
      className="divide-y divide-line rounded-lg border border-line bg-surface shadow-xs"
    >
      {members.map((member) => (
        <li key={member.id} className={`flex min-h-16 items-center gap-3 py-3 ${actions ? 'pr-2 pl-4' : 'px-4'}`}>
          <Avatar name={member.name} seed={member.id} size="md" />
          <div className="min-w-0 flex-1">
            <p className="flex min-w-0 items-center gap-2 text-sm font-medium text-ink">
              <span className="truncate" title={member.name}>
                {member.name}
              </span>
              {member.id === currentUserId && (
                <span className="shrink-0 rounded-full bg-canvas-strong px-2 text-[11px] leading-[18px] font-medium text-ink-muted">
                  {t('common:people.you')}
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
            <span className="sr-only">{t('common:rolePrefix')}</span>
            {roleLabel(member.role)}
          </span>
          {actions &&
            (isManageable(member, currentUserId) ? (
              <MemberActionsMenu
                memberName={member.name}
                onEdit={(opener) => actions.onEdit(member, opener)}
                onRemove={(opener) => actions.onRemove(member, opener)}
              />
            ) : (
              <span aria-hidden="true" className="size-8 shrink-0 pointer-coarse:size-10" />
            ))}
        </li>
      ))}
    </ul>
  )
}

/** The list's shape while the members load: a few rows as tall as the real ones. */
export function MembersSkeleton() {
  const { t } = useTranslation('members')
  return (
    <SkeletonFrame label={t('loading')}>
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
