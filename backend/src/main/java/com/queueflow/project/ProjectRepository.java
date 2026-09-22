package com.queueflow.project;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByWorkspaceIdAndKey(UUID workspaceId, String key);

    Optional<Project> findByWorkspaceIdAndKey(UUID workspaceId, String key);

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
    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") UUID id);
}
