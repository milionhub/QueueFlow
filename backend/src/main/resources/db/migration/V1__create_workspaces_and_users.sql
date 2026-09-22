-- Foundational schema: workspaces and users, with a 1:N workspace -> users relationship.
--
-- Identifier strategy: UUID primary keys generated with PostgreSQL 18's native
-- uuidv7() (time-ordered UUIDs). This avoids the b-tree index fragmentation
-- that random UUIDv4 causes on insert, while keeping the properties a
-- multi-tenant, potentially distributed application needs: globally unique
-- IDs that can be generated without a round-trip to a central sequence, are
-- safe to expose in URLs/APIs, and don't leak row counts the way a bigserial
-- id would.

CREATE TABLE workspaces (
    id         UUID        NOT NULL DEFAULT uuidv7(),
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_workspaces PRIMARY KEY (id)
);

CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT uuidv7(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    workspace_id  UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT fk_users_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
);

-- Explicit index: Postgres does not automatically index foreign key columns
-- (only the referenced side gets one, via the primary key). Every user of
-- this schema will list "users in workspace X" and the FK constraint itself
-- needs to check/lock rows in this column, so this index is load-bearing
-- for both application queries and referential-integrity performance.
CREATE INDEX idx_users_workspace_id ON users (workspace_id);
