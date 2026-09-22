-- Completes the V1 persistence model: workspace-scoped labels (many-to-many
-- with tickets), per-ticket comments, and an append-only ticket activity
-- timeline. Same conventions as V1-V3: UUID PK via uuidv7(), TIMESTAMPTZ
-- with DEFAULT now().

-- ============================================================
-- LABELS
-- ============================================================

CREATE TABLE labels (
    id           UUID        NOT NULL DEFAULT uuidv7(),
    -- Labels are short tags (e.g. "bug", "urgent", "frontend"), not
    -- descriptions. 50 chars comfortably covers real-world tag names
    -- (GitHub's own label length cap is the same order of magnitude) while
    -- keeping the field from being (mis)used as free text.
    name         VARCHAR(50) NOT NULL,
    workspace_id UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_labels PRIMARY KEY (id),
    CONSTRAINT fk_labels_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id),
    -- A label's name only needs to be unique within its own workspace; the
    -- same name legitimately reappears in a different workspace.
    CONSTRAINT uq_labels_workspace_name UNIQUE (workspace_id, name)
);

-- Join table for the Ticket <-> Label many-to-many relationship. The
-- composite primary key both identifies each row and prevents attaching the
-- same label to the same ticket twice - no surrogate id is needed for a
-- pure association table.
CREATE TABLE ticket_labels (
    ticket_id UUID NOT NULL,
    label_id  UUID NOT NULL,

    CONSTRAINT pk_ticket_labels PRIMARY KEY (ticket_id, label_id),
    CONSTRAINT fk_ticket_labels_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id),
    CONSTRAINT fk_ticket_labels_label FOREIGN KEY (label_id)
        REFERENCES labels (id)
);

-- The composite PK already gives a btree index led by ticket_id, covering
-- "labels on ticket X" (the direction Hibernate uses to load a ticket's
-- label collection). The reverse direction - "tickets carrying label Y" -
-- is a genuinely common ticket-tracker feature (filtering a board/list by
-- label), unlike the audit-style creator_id lookups skipped in V3, so it
-- earns its own index rather than being spequlative.
CREATE INDEX idx_ticket_labels_label_id ON ticket_labels (label_id);

-- NOTE: this schema does not (and, without denormalizing workspace_id onto
-- tickets/labels or adding a trigger, cannot cheaply) guarantee that a
-- ticket and a label attached to it belong to the same workspace. That
-- invariant is intentionally left to be enforced in the service layer in a
-- later phase, to avoid over-engineering the schema in V4.

-- ============================================================
-- COMMENTS
-- ============================================================

CREATE TABLE comments (
    id         UUID        NOT NULL DEFAULT uuidv7(),
    content    TEXT        NOT NULL,
    ticket_id  UUID        NOT NULL,
    author_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id),
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id)
        REFERENCES users (id)
);

-- The one query named for this table is "load comments for ticket X ordered
-- by created_at". A composite index with created_at as the second column
-- lets Postgres satisfy both the WHERE ticket_id = ? filter and the ORDER
-- BY created_at in a single index scan, with no separate sort step. A
-- ticket_id-only index would still need to sort the matching rows
-- afterwards, so the composite form is the one that actually matches the
-- access pattern.
CREATE INDEX idx_comments_ticket_id_created_at ON comments (ticket_id, created_at);

-- author_id is intentionally NOT indexed: "comments I wrote" is not a named
-- query pattern here, same reasoning as leaving tickets.creator_id
-- unindexed in V3.

-- ============================================================
-- ACTIVITIES
-- ============================================================

CREATE TABLE activities (
    id         UUID        NOT NULL DEFAULT uuidv7(),
    type       VARCHAR(30) NOT NULL,
    old_value  TEXT,
    new_value  TEXT,
    ticket_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- No updated_at: activity rows are immutable, append-only historical
    -- events, never edited after the fact.

    CONSTRAINT pk_activities PRIMARY KEY (id),
    CONSTRAINT fk_activities_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id),
    CONSTRAINT fk_activities_user FOREIGN KEY (user_id)
        REFERENCES users (id),
    -- VARCHAR(30): the longest current value, DESCRIPTION_CHANGED, is 20
    -- characters; 30 leaves headroom for future activity types without a
    -- column-width migration (same reasoning as tickets.status/priority).
    CONSTRAINT chk_activities_type CHECK (type IN (
        'TICKET_CREATED', 'STATUS_CHANGED', 'PRIORITY_CHANGED', 'ASSIGNEE_CHANGED',
        'TITLE_CHANGED', 'DESCRIPTION_CHANGED', 'LABEL_ADDED', 'LABEL_REMOVED'
    ))
);

-- Same composite-index reasoning as comments: "load activity for ticket X
-- chronologically" is satisfied by one index scan, no separate sort.
CREATE INDEX idx_activities_ticket_id_created_at ON activities (ticket_id, created_at);
