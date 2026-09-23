package com.queueflow.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByWorkspaceIdAndKey(UUID workspaceId, String key);

    /** Tenant-scoped: a project of another workspace is simply not found. */
    Optional<Project> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Project> findByWorkspaceIdAndKey(UUID workspaceId, String key);

    /**
     * Case-insensitive name order for the UI (without it, this database's
     * collation sorts every capitalized name before every lowercase one).
     * name then breaks case-only ties ("Alpha"/"alpha") and id breaks
     * genuinely equal names, which projects allow.
     */
    @Query("SELECT p FROM Project p WHERE p.workspace.id = :workspaceId ORDER BY LOWER(p.name) ASC, p.name ASC, p.id ASC")
    List<Project> findAllInWorkspaceSortedByName(@Param("workspaceId") UUID workspaceId);

    /**
     * Loads a Project while acquiring a database row-level pessimistic write
     * lock (Postgres: SELECT ... FOR UPDATE). Any other transaction trying
     * to lock or update the same row blocks until this transaction commits
     * or rolls back, at which point it re-reads the now-current row rather
     * than a stale snapshot - this is what makes per-project ticket-number
     * allocation safe under concurrency. A distinct method name is used
     * deliberately so ordinary reads via findById keep their normal,
     * non-locking semantics.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.workspace.id = :workspaceId")
    Optional<Project> findByIdAndWorkspaceIdForUpdate(@Param("id") UUID id,
            @Param("workspaceId") UUID workspaceId);
}
