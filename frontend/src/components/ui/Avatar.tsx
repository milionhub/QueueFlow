import { avatarTint, initials } from '../../features/auth/userDisplay'

type AvatarSize = 'xs' | 'sm' | 'md'

const SIZE_CLASSES: Record<AvatarSize, string> = {
  xs: 'size-5 text-[9px]',
  sm: 'size-6 text-[10px]',
  md: 'size-8 text-xs',
}

interface AvatarProps {
  name: string
  /** A stable identifier (the user's id): the tint follows it, not the name. */
  seed: string
  /** 20, 24 or 32px. */
  size?: AvatarSize
  className?: string
}

/**
 * A person's initials on a stable tint. Always decorative: the name is
 * next to it, or in accessible text, wherever it is used.
 */
export function Avatar({ name, seed, size = 'sm', className = '' }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full leading-none font-semibold tracking-tight select-none ${SIZE_CLASSES[size]} ${avatarTint(seed)} ${className}`}
    >
      {initials(name)}
    </span>
  )
}

/** The empty seat for "Unassigned": a dashed circle, no initials. */
export function EmptyAvatar({ size = 'sm', className = '' }: { size?: AvatarSize; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 rounded-full border border-dashed border-ink-subtle/60 ${SIZE_CLASSES[size]} ${className}`}
    />
  )
}
