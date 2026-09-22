-- Tickets belong to a single project (N:1). A ticket's human-readable key
-- (e.g. ECOM-7) is never persisted: it is derived at read time from
-- projects.key + '-' + tickets.ticket_number.
--
-- Concurrency-safe numbering: ticket_number is NOT computed as
-- MAX(ticket_number) + 1 (racy under concurrent inserts). Instead each
-- project carries its own next_ticket_number counter. The future
-- service-layer allocator will lock the project row transactionally, read
-- next_ticket_number, assign it to the new ticket, and increment the
-- counter in the same transaction. This migration only adds the schema
-- support for that design (the column and its constraint) - no allocation
-- logic lives here.

ALTER TABLE projects
    ADD COLUMN next_ticket_number BIGINT NOT NULL DEFAULT 1;

ALTER TABLE projects
    ADD CONSTRAINT chk_projects_next_ticket_number_positive
        CHECK (next_ticket_number >= 1);

CREATE TABLE tickets (
    id            UUID         NOT NULL DEFAULT uuidv7(),
    ticket_number BIGINT       NOT NULL,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(20)  NOT NULL,
    priority      VARCHAR(20)  NOT NULL,
    project_id    UUID         NOT NULL,
    creator_id    UUID         NOT NULL,
    assignee_id   UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT fk_tickets_project FOREIGN KEY (project_id)
        REFERENCES projects (id),
    CONSTRAINT fk_tickets_creator FOREIGN KEY (creator_id)
        REFERENCES users (id),
    CONSTRAINT fk_tickets_assignee FOREIGN KEY (assignee_id)
        REFERENCES users (id),
    -- A ticket's number only needs to be unique within its own project; the
    -- same number legitimately reappears in a different project.
    CONSTRAINT uq_tickets_project_ticket_number UNIQUE (project_id, ticket_number),
    CONSTRAINT chk_tickets_ticket_number_positive CHECK (ticket_number >= 1),
    CONSTRAINT chk_tickets_status CHECK (
        status IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE')
    ),
    CONSTRAINT chk_tickets_priority CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    )
);

-- UNIQUE(project_id, ticket_number) already creates a btree index led by
-- project_id, which covers "tickets in project X" lookups via the leftmost
-- prefix - no separate project_id index needed (same reasoning as V2).
--
-- assignee_id: indexed. "Tickets assigned to me" is a core, high-frequency
-- view in a ticket queue application, and this FK has no other index to
-- piggyback on, so it needs its own (same situation V1 had for
-- users.workspace_id).
--
-- creator_id: NOT indexed. "Tickets I created" is a plausible but
-- secondary/audit-style query, not a core queue workflow, and there is no
-- concrete requirement for it yet. Adding the index now would be
-- speculative write overhead with no proven read pattern to justify it; it
-- is cheap to add in a later migration if that need materializes.
CREATE INDEX idx_tickets_assignee_id ON tickets (assignee_id);
