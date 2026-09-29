import { MessageSquare, MoreHorizontal, Pencil, SendHorizontal, Trash2 } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'

import { createComment, deleteComment, updateComment, type Comment } from '../../../api/comments'
import { Alert } from '../../../components/ui/Alert'
import { Avatar } from '../../../components/ui/Avatar'
import { Button } from '../../../components/ui/Button'
import { Dialog, DialogBody, DialogFooter } from '../../../components/ui/Dialog'
import { IconButton } from '../../../components/ui/IconButton'
import { LoadError } from '../../../components/ui/LoadError'
import { SkeletonFrame } from '../../../components/ui/States'
import { TextAreaField } from '../../../components/ui/TextAreaField'
import { focusAtEnd } from '../../../lib/focusAtEnd'
import { isKeyboardModality } from '../../../lib/inputModality'
import { formatRelativeTime } from '../../../lib/relativeTime'
import { useAutoGrow } from '../../../lib/useAutoGrow'
import { useAuth } from '../../auth/useAuth'
import { commentChangeError, isCommentGone } from '../commentErrors'
import { isTicketGone } from '../ticketErrors'
import { useTicketComments } from '../useTicketComments'

const IS_MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/.test(navigator.userAgent)
const SEND_SHORTCUT = IS_MAC ? '⌘+Enter' : 'Ctrl+Enter'
/** Consecutive comments by one author within this time share one header. */
const GROUP_WINDOW = 5 * 60_000

/** Ctrl+Enter (⌘+Enter on a Mac) submits the text area's form, like its submit button. */
function submitOnShortcut(event: KeyboardEvent<HTMLTextAreaElement>) {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey) && !event.nativeEvent.isComposing) {
    event.preventDefault()
    event.currentTarget.form?.requestSubmit()
  }
}

/** Focus requests handled after render, when the target exists (see EditableTicketText). */
function useFocusRequest<Target extends string>(focusOn: (target: Target) => void) {
  const [request, setRequest] = useState<{ to: Target; n: number } | null>(null)
  const focusOnRef = useRef(focusOn)
  useEffect(() => {
    focusOnRef.current = focusOn
  })
  useEffect(() => {
    if (request) {
      focusOnRef.current(request.to)
    }
  }, [request])
  return (to: Target) => setRequest((current) => ({ to, n: (current?.n ?? 0) + 1 }))
}

/** Whether `comment` continues the previous one: same author, within five minutes. */
function continues(previous: Comment | undefined, comment: Comment): boolean {
  return (
    previous !== undefined &&
    previous.authorId === comment.authorId &&
    Date.parse(comment.createdAt) - Date.parse(previous.createdAt) <= GROUP_WINDOW
  )
}

interface TicketCommentsProps {
  ticketId: string
  announce: (message: string) => void
  /** The backend says the ticket no longer exists. */
  onTicketGone: () => void
}

/**
 * The ticket's comments as a conversation, oldest first, always aligned to
 * the left (this is a tracker, not a chat), and a composer below them.
 * Anyone in the workspace may comment; only a comment's author gets Edit
 * and Delete (the backend enforces it either way). Comments do not touch
 * the ticket or its history, so nothing else reloads.
 */
export function TicketComments({ ticketId, announce, onTicketGone }: TicketCommentsProps) {
  const { t } = useTranslation('tickets')
  const { user } = useAuth()
  const comments = useTicketComments(ticketId)
  const composerRef = useRef<HTMLDivElement>(null)
  const [freshIds, setFreshIds] = useState<ReadonlySet<string>>(new Set())
  const { state } = comments
  const composerInput = () => composerRef.current?.querySelector('textarea') ?? null

  function added(comment: Comment) {
    comments.added(comment)
    setFreshIds((current) => new Set(current).add(comment.id))
  }

  return (
    <section aria-labelledby="ticket-comments" className="min-w-0">
      <h2 id="ticket-comments" className="mb-4 flex items-center gap-2 text-sm font-semibold text-ink">
        <span
          aria-hidden="true"
          className="flex size-6 items-center justify-center rounded-md bg-accent-subtle text-accent"
        >
          <MessageSquare className="size-3.5" strokeWidth={2.25} />
        </span>
        {t('comments.title')}
        {state.status === 'ready' && (
          <span className="rounded-full bg-accent-subtle px-2 text-xs leading-5 font-semibold text-accent tabular-nums">
            {state.data.length}
          </span>
        )}
      </h2>
      {(state.status === 'loading' || state.status === 'idle') && <CommentsSkeleton />}
      {state.status === 'error' && (
        <LoadError
          message={t('comments.loadError')}
          reason={state.reason}
          onRetry={comments.retry}
          size="inline"
        />
      )}
      {state.status === 'ready' && (
        <>
          {state.data.length === 0 ? (
            <p className="mb-4 flex items-center gap-2 text-sm text-ink-subtle">
              <MessageSquare aria-hidden="true" className="size-4" strokeWidth={2} />
              {t('comments.empty')}
            </p>
          ) : (
            <ol className="mb-5 flex flex-col">
              {state.data.map((comment, index) => (
                <CommentItem
                  key={comment.id}
                  comment={comment}
                  continued={continues(state.data[index - 1], comment)}
                  fresh={freshIds.has(comment.id)}
                  now={state.receivedAt}
                  own={comment.authorId === user?.id}
                  onUpdated={comments.updated}
                  onRemoved={comments.removed}
                  announce={announce}
                  onTicketGone={onTicketGone}
                  composerInput={composerInput}
                />
              ))}
            </ol>
          )}
          <CommentComposer
            ticketId={ticketId}
            containerRef={composerRef}
            onAdded={added}
            announce={announce}
            onTicketGone={onTicketGone}
          />
        </>
      )}
    </section>
  )
}

interface CommentComposerProps {
  ticketId: string
  containerRef: RefObject<HTMLDivElement | null>
  onAdded: (comment: Comment) => void
  announce: (message: string) => void
  onTicketGone: () => void
}

/**
 * Always there once the comments have loaded: the signed-in user's avatar
 * and a box that grows from one to six lines. A failed send keeps the text.
 */
function CommentComposer({ ticketId, containerRef, onAdded, announce, onTicketGone }: CommentComposerProps) {
  const { t } = useTranslation('tickets')
  const { authorizedRequest, user } = useAuth()
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)
  const textAreaRef = useRef<HTMLTextAreaElement>(null)
  const id = useId()
  const errorId = `${id}-error`
  const focus = useFocusRequest<'input'>(() => textAreaRef.current?.focus())
  useAutoGrow(textAreaRef, content, { minRows: 1, maxRows: 6 })

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    const trimmed = content.trim()
    if (trimmed === '') {
      setError(t('comments.write'))
      focus('input')
      return
    }
    submitting.current = true
    setPending(true)
    setError(null)
    // The box is disabled while sending, which drops its focus. It gets it
    // back after a failure (to fix and resend) and, when sent from the
    // keyboard (the shortcut submits with no submitter button), after a
    // success to write the next one. After a tap or click on Comment it
    // stays unfocused, so the box does not look selected.
    let refocus = isKeyboardModality() || (event.nativeEvent as SubmitEvent).submitter === null
    try {
      const comment = await createComment(authorizedRequest, ticketId, trimmed)
      onAdded(comment)
      setContent('')
      announce(t('comments.added'))
    } catch (failure) {
      refocus = true
      setError(commentChangeError(failure, 'add'))
      if (isTicketGone(failure)) {
        onTicketGone()
      }
    } finally {
      submitting.current = false
      setPending(false)
      if (refocus) {
        focus('input')
      }
    }
  }

  return (
    <div ref={containerRef} className="flex gap-3">
      {user && <Avatar name={user.name} seed={user.id} size="md" className="mt-1 max-sm:hidden" />}
      <form noValidate aria-busy={pending} onSubmit={handleSubmit} className="min-w-0 flex-1">
        <div
          className={`rounded-lg border bg-surface shadow-xs transition-[border-color,box-shadow] duration-150 has-[textarea:focus-visible]:border-accent keyboard:has-[textarea:focus-visible]:outline-2 has-[textarea:focus-visible]:outline-offset-2 has-[textarea:focus-visible]:outline-accent ${
            error ? 'border-danger' : 'border-line hover:border-line-strong'
          }`}
        >
          <label htmlFor={id} className="sr-only">
            {t('comments.composerLabel')}
          </label>
          <textarea
            ref={textAreaRef}
            id={id}
            name="comment"
            rows={1}
            value={content}
            onChange={(event) => setContent(event.target.value)}
            onKeyDown={submitOnShortcut}
            aria-keyshortcuts="Control+Enter Meta+Enter"
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            disabled={pending}
            placeholder={t('comments.placeholder')}
            className="block w-full resize-none rounded-t-lg bg-transparent px-3 pt-2.5 pb-1 text-base leading-6 text-ink outline-none placeholder:text-ink-subtle disabled:opacity-70 sm:text-sm"
          />
          <div className="flex items-center justify-end gap-3 px-2 pb-2">
            <span aria-hidden="true" className="hidden text-xs text-ink-subtle sm:inline">
              {t('comments.sendHint', { shortcut: SEND_SHORTCUT })}
            </span>
            <Button type="submit" size="sm" disabled={pending} loading={pending} className="max-sm:size-9 max-sm:px-0">
              {!pending && <SendHorizontal aria-hidden="true" className="size-4" strokeWidth={2} />}
              <span className="max-sm:sr-only">{pending ? t('comments.sending') : t('comments.send')}</span>
            </Button>
          </div>
        </div>
        {error && (
          <p id={errorId} className="mt-1.5 animate-fade-in text-xs leading-5 font-medium text-danger">
            {error}
          </p>
        )}
      </form>
    </div>
  )
}

interface CommentItemProps {
  comment: Comment
  /** Follows a comment by the same author within five minutes: no avatar or header of its own. */
  continued: boolean
  /** Just posted here: it fades in. */
  fresh: boolean
  now: number
  /** The signed-in user wrote it: Edit and Delete are offered. */
  own: boolean
  onUpdated: (comment: Comment) => void
  onRemoved: (commentId: string) => void
  announce: (message: string) => void
  onTicketGone: () => void
  /** Where focus goes when this comment disappears. */
  composerInput: () => HTMLElement | null
}

function CommentItem({
  comment,
  continued,
  fresh,
  now,
  own,
  onUpdated,
  onRemoved,
  announce,
  onTicketGone,
  composerInput,
}: CommentItemProps) {
  const { t } = useTranslation(['tickets', 'common'])
  const { authorizedRequest } = useAuth()
  const [draft, setDraft] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  /** An edit found the comment deleted elsewhere: it leaves the list once the edit is closed. */
  const [gone, setGone] = useState(false)
  const [confirmOpener, setConfirmOpener] = useState<HTMLElement | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const requestRunning = useRef(false)
  const editButtonRef = useRef<HTMLButtonElement>(null)
  const menuTriggerRef = useRef<HTMLButtonElement>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const confirmButtonRef = useRef<HTMLButtonElement>(null)
  const focus = useFocusRequest<'input' | 'edit' | 'confirm'>((to) => {
    if (to === 'edit') {
      // Pointer screens show the pencil; touch screens the "⋯" menu instead.
      const pencil = editButtonRef.current
      if (pencil && pencil.offsetParent !== null) {
        pencil.focus()
      } else {
        menuTriggerRef.current?.focus()
      }
    } else if (to === 'confirm') {
      confirmButtonRef.current?.focus()
    } else {
      focusAtEnd(formRef.current?.querySelector('textarea'))
    }
  })

  const created = formatRelativeTime(comment.createdAt, now)
  const edited = comment.updatedAt !== comment.createdAt
  const updated = formatRelativeTime(comment.updatedAt, now)

  function startEditing() {
    setDraft(comment.content)
    setError(null)
    focus('input')
  }

  function cancelEditing() {
    if (saving) {
      return
    }
    if (gone) {
      onRemoved(comment.id)
      announce(t('comments.removedGone'))
      composerInput()?.focus()
      return
    }
    setDraft(null)
    setError(null)
    focus('edit')
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (draft === null || requestRunning.current) {
      return
    }
    const trimmed = draft.trim()
    if (trimmed === '') {
      setError(t('comments.write'))
      focus('input')
      return
    }
    if (trimmed === comment.content.trim()) {
      setDraft(null)
      setError(null)
      focus('edit')
      return
    }
    requestRunning.current = true
    setSaving(true)
    setError(null)
    try {
      const saved = await updateComment(authorizedRequest, comment.id, trimmed)
      onUpdated(saved)
      setDraft(null)
      announce(t('comments.updated'))
      focus('edit')
    } catch (failure) {
      setError(commentChangeError(failure, 'edit'))
      if (isCommentGone(failure)) {
        setGone(true)
      } else if (isTicketGone(failure)) {
        onTicketGone()
      }
      focus('input')
    } finally {
      requestRunning.current = false
      setSaving(false)
    }
  }

  function openConfirm(opener: HTMLElement) {
    setConfirmOpener(opener)
    setDeleteError(null)
    setConfirming(true)
  }

  function closeConfirm() {
    if (!deleting) {
      setConfirming(false)
    }
  }

  async function confirmDelete() {
    if (requestRunning.current) {
      return
    }
    requestRunning.current = true
    setDeleting(true)
    setDeleteError(null)
    try {
      await deleteComment(authorizedRequest, comment.id)
      onRemoved(comment.id)
      announce(t('comments.deleted'))
    } catch (failure) {
      if (isCommentGone(failure)) {
        // Already deleted elsewhere: what was asked for is done.
        onRemoved(comment.id)
        announce(t('comments.deleted'))
      } else {
        setDeleteError(commentChangeError(failure, 'delete'))
        // The button was disabled while deleting, which dropped focus: back to it, to try again.
        focus('confirm')
        if (isTicketGone(failure)) {
          onTicketGone()
        }
      }
    } finally {
      requestRunning.current = false
      setDeleting(false)
    }
  }

  const time = (
    <time dateTime={comment.createdAt} title={created.full} className="text-xs text-ink-subtle tabular-nums">
      <span aria-hidden="true">{created.short}</span>
      <span className="sr-only">{created.spoken}</span>
    </time>
  )
  const editedMark = edited && (
    <span className="text-xs text-ink-subtle" title={t('comments.editedAt', { date: updated.full })}>
      {t('comments.edited')}
    </span>
  )

  return (
    <li
      className={`group/comment flex min-w-0 gap-3 ${continued ? 'mt-1' : 'mt-5 first:mt-0'} ${fresh ? 'animate-enter' : ''}`}
    >
      <div className="w-8 shrink-0 max-sm:w-7">
        {!continued && <Avatar name={comment.authorName} seed={comment.authorId} size="md" className="max-sm:size-7" />}
      </div>
      <div className="min-w-0 flex-1">
        {continued ? (
          // Grouped under the previous comment's header: who and when stay available, and on hover.
          <p className="sr-only">
            {comment.authorName}, {created.spoken}
            {edited ? t('comments.editedSpoken') : ''}
          </p>
        ) : (
          <div className="mb-1 flex min-h-5 flex-wrap items-baseline gap-x-2 gap-y-0.5">
            <span className="min-w-0 text-sm font-semibold break-words text-ink">{comment.authorName}</span>
            {time}
            {editedMark}
          </div>
        )}

        {draft === null ? (
          <div className="flex min-w-0 items-start gap-1">
            <div
              className={`min-w-0 rounded-lg px-3 py-2 max-sm:flex-1 ${
                continued ? '' : 'rounded-tl-sm'
              } ${own ? 'bg-accent-subtle/70' : 'bg-surface ring-1 ring-line'}`}
            >
              <p className="text-[15px] leading-6 break-words whitespace-pre-wrap text-ink sm:text-sm">
                {comment.content}
              </p>
              {continued && edited && <p className="mt-0.5">{editedMark}</p>}
            </div>
            {continued && (
              <span
                aria-hidden="true"
                className="mt-2 hidden shrink-0 text-xs text-ink-subtle tabular-nums opacity-0 transition-opacity duration-150 group-hover/comment:opacity-100 sm:inline"
                title={created.full}
              >
                {created.short}
              </span>
            )}
            {own && (
              <CommentActions
                editButtonRef={editButtonRef}
                menuTriggerRef={menuTriggerRef}
                onEdit={startEditing}
                onDelete={openConfirm}
              />
            )}
          </div>
        ) : (
          <form ref={formRef} noValidate aria-busy={saving} onSubmit={handleSave} className="flex flex-col gap-2">
            <TextAreaField
              label={t('comments.editLabel')}
              labelHidden
              name="comment"
              rows={2}
              maxRows={12}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  event.preventDefault()
                  cancelEditing()
                } else {
                  submitOnShortcut(event)
                }
              }}
              aria-keyshortcuts="Control+Enter Meta+Enter Escape"
              error={error ?? undefined}
              disabled={saving}
            />
            <div className="flex flex-wrap items-center gap-2">
              <Button type="submit" size="sm" disabled={saving || gone} loading={saving}>
                {saving ? t('common:actions.saving') : t('common:actions.save')}
              </Button>
              <Button variant="ghost" size="sm" onClick={cancelEditing} disabled={saving}>
                {t('common:actions.cancel')}
              </Button>
              <span aria-hidden="true" className="ml-auto hidden text-xs text-ink-subtle sm:inline">
                {t('comments.saveHint', { shortcut: SEND_SHORTCUT })}
              </span>
            </div>
          </form>
        )}
      </div>
      {confirming && (
        <Dialog
          title={t('comments.deleteTitle')}
          description={t('comments.deleteDescription')}
          icon={Trash2}
          tone="danger"
          onClose={closeConfirm}
          dismissible={!deleting}
          returnFocus={confirmOpener}
          fallbackFocus={composerInput}
        >
          {deleteError && (
            <DialogBody>
              <Alert tone="error">{deleteError}</Alert>
            </DialogBody>
          )}
          <DialogFooter>
            <Button variant="secondary" onClick={closeConfirm} disabled={deleting} data-autofocus>
              {t('common:actions.cancel')}
            </Button>
            <Button
              ref={confirmButtonRef}
              variant="danger"
              onClick={() => void confirmDelete()}
              disabled={deleting}
              loading={deleting}
            >
              {deleting ? t('common:actions.deleting') : t('common:actions.delete')}
            </Button>
          </DialogFooter>
        </Dialog>
      )}
    </li>
  )
}

interface CommentActionsProps {
  editButtonRef: RefObject<HTMLButtonElement | null>
  menuTriggerRef: RefObject<HTMLButtonElement | null>
  onEdit: () => void
  /** `opener` is what focus returns to when the confirmation closes. */
  onDelete: (opener: HTMLElement) => void
}

/**
 * Edit and Delete of one's own comment. With a mouse or trackpad: two
 * quiet icon buttons, shown on hover or when either has keyboard focus.
 * On touch screens, where there is no hover: a visible "⋯" button opening
 * a small menu with both.
 */
function CommentActions({ editButtonRef, menuTriggerRef, onEdit, onDelete }: CommentActionsProps) {
  const { t } = useTranslation(['tickets', 'common'])
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    containerRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      setOpen(false)
      menuTriggerRef.current?.focus()
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const items = [...(containerRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]
      const index = items.indexOf(document.activeElement as HTMLElement)
      const step = event.key === 'ArrowDown' ? 1 : -1
      items[(index + step + items.length) % items.length]?.focus()
    }
  }

  return (
    <>
      <div className="flex shrink-0 items-center opacity-0 transition-opacity duration-150 group-focus-within/comment:opacity-100 group-hover/comment:opacity-100 pointer-coarse:hidden">
        <IconButton ref={editButtonRef} icon={Pencil} label={t('comments.editLabel')} size="sm" onClick={onEdit} />
        <IconButton
          icon={Trash2}
          label={t('comments.deleteLabel')}
          size="sm"
          onClick={(event) => onDelete(event.currentTarget)}
          className="hover:text-danger!"
        />
      </div>
      <div ref={containerRef} className="relative hidden shrink-0 pointer-coarse:block">
        <IconButton
          ref={menuTriggerRef}
          icon={MoreHorizontal}
          label={t('comments.actions')}
          title=""
          size="sm"
          aria-haspopup="menu"
          aria-expanded={open}
          aria-controls={open ? menuId : undefined}
          onClick={() => setOpen((current) => !current)}
        />
        {open && (
          <div
            id={menuId}
            role="menu"
            aria-label={t('comments.actions')}
            onKeyDown={handleMenuKeyDown}
            className="absolute top-full right-0 z-20 mt-1 w-40 origin-top-right animate-pop rounded-lg border border-line bg-surface p-1 shadow-lg"
          >
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setOpen(false)
                onEdit()
              }}
              className="flex h-11 w-full items-center gap-2 rounded-md px-2.5 text-sm text-ink hover:bg-canvas-strong focus-visible:bg-canvas-strong"
            >
              <Pencil aria-hidden="true" className="size-4 text-ink-muted" strokeWidth={2} />
              {t('common:actions.edit')}
            </button>
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setOpen(false)
                if (menuTriggerRef.current) {
                  onDelete(menuTriggerRef.current)
                }
              }}
              className="flex h-11 w-full items-center gap-2 rounded-md px-2.5 text-sm text-danger hover:bg-danger/5 focus-visible:bg-danger/5"
            >
              <Trash2 aria-hidden="true" className="size-4" strokeWidth={2} />
              {t('common:actions.delete')}
            </button>
          </div>
        )}
      </div>
    </>
  )
}

function CommentsSkeleton() {
  const { t } = useTranslation('tickets')
  return (
    <SkeletonFrame label={t('comments.loading')}>
      <div className="flex flex-col gap-5">
        {[0, 1].map((row) => (
          <div key={row} className="flex gap-3">
            <div className="skeleton size-8 shrink-0 rounded-full!" />
            <div className="flex flex-1 flex-col gap-2">
              <div className="skeleton h-3 w-32" />
              <div className="skeleton h-14 w-full max-w-md rounded-lg!" />
            </div>
          </div>
        ))}
      </div>
    </SkeletonFrame>
  )
}
