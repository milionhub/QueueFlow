import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type RefObject } from 'react'

import { createComment, deleteComment, updateComment, type Comment } from '../../../api/comments'
import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import { Dialog, DialogFooter } from '../../../components/ui/Dialog'
import { LoadError } from '../../../components/ui/LoadError'
import { TextAreaField } from '../../../components/ui/TextAreaField'
import { formatRelativeTime } from '../../../lib/relativeTime'
import { useAuth } from '../../auth/useAuth'
import { commentChangeError, isCommentGone } from '../commentErrors'
import { isTicketGone } from '../ticketErrors'
import { useTicketComments } from '../useTicketComments'

const BLOCK = 'rounded bg-line motion-safe:animate-pulse'
const IS_MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/.test(navigator.userAgent)
const SEND_SHORTCUT = IS_MAC ? '⌘+Enter' : 'Ctrl+Enter'
const ROW_ACTION =
  'inline-flex h-8 items-center rounded-md px-2 text-xs font-medium text-ink-muted hover:bg-canvas hover:text-ink disabled:cursor-not-allowed disabled:opacity-60'

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

interface TicketCommentsProps {
  ticketId: string
  announce: (message: string) => void
  /** The backend says the ticket no longer exists. */
  onTicketGone: () => void
}

/**
 * The ticket's comments, oldest first, and a composer below them. Anyone in
 * the workspace may comment; only a comment's author gets Edit and Delete
 * (the backend enforces it either way). Comments do not touch the ticket
 * or its history, so nothing else reloads.
 */
export function TicketComments({ ticketId, announce, onTicketGone }: TicketCommentsProps) {
  const { user } = useAuth()
  const comments = useTicketComments(ticketId)
  const composerRef = useRef<HTMLDivElement>(null)
  const { state } = comments
  const composerInput = () => composerRef.current?.querySelector('textarea') ?? null

  return (
    <section aria-labelledby="ticket-comments" className="min-w-0 border-t border-line pt-5">
      <h3 id="ticket-comments" className="mb-3 flex items-center gap-2 text-sm font-semibold text-ink">
        Comments
        {state.status === 'ready' && (
          <span className="rounded-full border border-line bg-canvas px-1.5 text-xs leading-5 font-medium text-ink-muted tabular-nums">
            {state.data.length}
          </span>
        )}
      </h3>
      {(state.status === 'loading' || state.status === 'idle') && <CommentsSkeleton />}
      {state.status === 'error' && (
        <LoadError message="The comments could not be loaded." reason={state.reason} onRetry={comments.retry} />
      )}
      {state.status === 'ready' && (
        <>
          {state.data.length === 0 ? (
            <p className="text-sm text-ink-subtle">No comments yet.</p>
          ) : (
            <ol className="flex flex-col divide-y divide-line">
              {state.data.map((comment) => (
                <CommentItem
                  key={comment.id}
                  comment={comment}
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
            onAdded={comments.added}
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

/** Always there once the comments have loaded. A failed send keeps the text. */
function CommentComposer({ ticketId, containerRef, onAdded, announce, onTicketGone }: CommentComposerProps) {
  const { authorizedRequest } = useAuth()
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)
  const focus = useFocusRequest<'input'>(() => containerRef.current?.querySelector('textarea')?.focus())

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    const trimmed = content.trim()
    if (trimmed === '') {
      setError('Write a comment.')
      focus('input')
      return
    }
    submitting.current = true
    setPending(true)
    setError(null)
    try {
      const comment = await createComment(authorizedRequest, ticketId, trimmed)
      onAdded(comment)
      setContent('')
      announce('Comment added.')
    } catch (failure) {
      setError(commentChangeError(failure, 'add'))
      if (isTicketGone(failure)) {
        onTicketGone()
      }
    } finally {
      submitting.current = false
      setPending(false)
      focus('input')
    }
  }

  return (
    <div ref={containerRef} className="mt-4">
      <form noValidate aria-busy={pending} onSubmit={handleSubmit} className="flex flex-col gap-2">
        <TextAreaField
          label="Add a comment"
          name="comment"
          rows={3}
          value={content}
          onChange={(event) => setContent(event.target.value)}
          onKeyDown={submitOnShortcut}
          aria-keyshortcuts="Control+Enter Meta+Enter"
          error={error ?? undefined}
          disabled={pending}
        />
        <div className="flex items-center justify-end gap-3">
          <span aria-hidden="true" className="hidden text-xs text-ink-subtle sm:inline">
            {SEND_SHORTCUT} to send
          </span>
          <Button type="submit" size="sm" disabled={pending}>
            {pending ? 'Commenting…' : 'Comment'}
          </Button>
        </div>
      </form>
    </div>
  )
}

interface CommentItemProps {
  comment: Comment
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
  now,
  own,
  onUpdated,
  onRemoved,
  announce,
  onTicketGone,
  composerInput,
}: CommentItemProps) {
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
  const formRef = useRef<HTMLFormElement>(null)
  const confirmButtonRef = useRef<HTMLButtonElement>(null)
  const focus = useFocusRequest<'input' | 'edit' | 'confirm'>((to) => {
    if (to === 'edit') {
      editButtonRef.current?.focus()
    } else if (to === 'confirm') {
      confirmButtonRef.current?.focus()
    } else {
      formRef.current?.querySelector('textarea')?.focus()
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
      announce('The comment was removed: it no longer exists.')
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
      setError('Write a comment.')
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
      announce('Comment updated.')
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
      announce('Comment deleted.')
    } catch (failure) {
      if (isCommentGone(failure)) {
        // Already deleted elsewhere: what was asked for is done.
        onRemoved(comment.id)
        announce('Comment deleted.')
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

  return (
    <li className="min-w-0 py-3 first:pt-0">
      <div className="flex min-h-8 flex-wrap items-center gap-x-2 gap-y-0.5">
        <span className="min-w-0 text-sm font-medium break-words text-ink">{comment.authorName}</span>
        <time dateTime={comment.createdAt} title={created.full} className="text-xs text-ink-subtle tabular-nums">
          <span aria-hidden="true">{created.short}</span>
          <span className="sr-only">{created.spoken}</span>
        </time>
        {edited && (
          <span className="text-xs text-ink-subtle" title={`Edited ${updated.full}`}>
            (edited)
          </span>
        )}
        {own && draft === null && (
          <div className="ml-auto flex gap-1">
            <button ref={editButtonRef} type="button" onClick={startEditing} className={ROW_ACTION}>
              Edit<span className="sr-only"> comment</span>
            </button>
            <button type="button" onClick={(event) => openConfirm(event.currentTarget)} className={ROW_ACTION}>
              Delete<span className="sr-only"> comment</span>
            </button>
          </div>
        )}
      </div>
      {draft === null ? (
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-wrap text-ink">{comment.content}</p>
      ) : (
        <form ref={formRef} noValidate aria-busy={saving} onSubmit={handleSave} className="mt-2 flex flex-col gap-2">
          <TextAreaField
            label="Edit comment"
            name="comment"
            rows={4}
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
          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={saving || gone}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
            <Button variant="secondary" size="sm" onClick={cancelEditing} disabled={saving}>
              Cancel
            </Button>
          </div>
        </form>
      )}
      {confirming && (
        <Dialog
          title="Delete comment"
          description="Delete this comment? This action cannot be undone."
          onClose={closeConfirm}
          dismissible={!deleting}
          returnFocus={confirmOpener}
          fallbackFocus={composerInput}
        >
          <div className={deleteError ? 'px-5 pt-4 pb-5' : 'pb-5'}>
            {deleteError && <Alert tone="error">{deleteError}</Alert>}
          </div>
          <DialogFooter>
            <Button variant="secondary" onClick={closeConfirm} disabled={deleting} data-autofocus>
              Cancel
            </Button>
            <Button ref={confirmButtonRef} variant="danger" onClick={() => void confirmDelete()} disabled={deleting}>
              {deleting ? 'Deleting…' : 'Delete'}
            </Button>
          </DialogFooter>
        </Dialog>
      )}
    </li>
  )
}

function CommentsSkeleton() {
  return (
    <div aria-busy="true">
      <span className="sr-only" role="status">
        Loading comments…
      </span>
      <div aria-hidden="true" className="flex flex-col gap-4">
        {[0, 1].map((row) => (
          <div key={row} className="flex flex-col gap-2">
            <div className={`h-3 w-32 ${BLOCK}`} />
            <div className={`h-3 w-full ${BLOCK}`} />
            <div className={`h-3 w-2/3 ${BLOCK}`} />
          </div>
        ))}
      </div>
    </div>
  )
}
