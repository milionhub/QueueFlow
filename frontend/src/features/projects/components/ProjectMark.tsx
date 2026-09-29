import { avatarTint } from '../../auth/userDisplay'

const SIZE_CLASSES = {
  sm: 'size-7 rounded-md text-xs',
  md: 'size-9 rounded-lg text-sm',
  lg: 'size-12 rounded-xl text-lg',
} as const

/**
 * A project's mark: the first character of its key on a tile whose tint
 * follows the project's id, so a project looks the same everywhere.
 * Decorative: the key and name are always next to it.
 */
export function ProjectMark({
  projectId,
  projectKey,
  size = 'md',
  className = '',
}: {
  projectId: string
  projectKey: string
  size?: keyof typeof SIZE_CLASSES
  className?: string
}) {
  return (
    <span
      aria-hidden="true"
      className={`flex shrink-0 items-center justify-center font-bold ring-1 ring-black/5 ring-inset ${SIZE_CLASSES[size]} ${avatarTint(projectId)} ${className}`}
    >
      {projectKey.charAt(0)}
    </span>
  )
}
