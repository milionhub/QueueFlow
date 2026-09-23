package com.queueflow.label;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    Optional<Label> findByWorkspaceIdAndName(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);

    /**
     * Case-insensitive name order for the UI (without it, this database's
     * collation sorts every capitalized name before every lowercase one).
     * Ordering only - lookup and uniqueness stay exact-case, so "Bug" and
     * "bug" can coexist; name then breaks that case-only tie and id keeps
     * the order deterministic regardless.
     */
    @Query("SELECT l FROM Label l WHERE l.workspace.id = :workspaceId ORDER BY LOWER(l.name) ASC, l.name ASC, l.id ASC")
    List<Label> findAllInWorkspaceSortedByName(@Param("workspaceId") UUID workspaceId);
}
