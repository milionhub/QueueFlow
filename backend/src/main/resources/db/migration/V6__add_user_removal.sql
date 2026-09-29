-- An ADMIN can remove a MEMBER from their workspace (Phase 3.10E). The
-- user's row is kept, not deleted: tickets.creator_id, comments.author_id
-- and activities.user_id reference it NOT NULL, and deleting it would mean
-- deleting (or rewriting) that member's share of the workspace's history.
--
-- removed_at marks the row as a former member instead. The application
-- leaves such a user out of everything that concerns current members
-- (member list, assignee lookup, login, authenticating a request), while
-- the history they took part in keeps pointing at a real row with their
-- name. At removal the application also replaces the email with a unique
-- placeholder (freeing the address for a new account) and the password
-- hash with a value that can never match - see User.markRemoved.
--
-- No index: every query that filters on it already filters on users.id or
-- users.workspace_id, which are indexed.
ALTER TABLE users
    ADD COLUMN removed_at TIMESTAMPTZ;
