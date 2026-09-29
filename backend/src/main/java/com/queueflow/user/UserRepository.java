package com.queueflow.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every lookup except the inherited JpaRepository ones and existsByEmail
 * sees current members only: a user removed from their workspace (removed_at
 * set, see V6 and User.markRemoved) cannot log in, authenticate a request,
 * be listed, looked up or assigned. Their row stays for the history that
 * references it, which reaches it through those associations, not here.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Global lookup, for login only: at that point there is no workspace yet.
     * Domain code uses the workspace-scoped lookups below.
     */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.removedAt IS NULL")
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * Global by design, for registration and member creation: emails are
     * unique across QueueFlow (V1/V5 unique indexes). Answers only a 409
     * that names no account or workspace.
     */
    boolean existsByEmail(String email);

    /** Tenant-scoped: a user of another workspace - or a removed one - is simply not found. */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.workspace.id = :workspaceId AND u.removedAt IS NULL")
    Optional<User> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    /** Tenant-scoped: emails of other workspaces cannot be probed. */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.workspace.id = :workspaceId AND u.removedAt IS NULL")
    Optional<User> findByEmailAndWorkspaceId(@Param("email") String email, @Param("workspaceId") UUID workspaceId);

    /**
     * Current id, workspace and role of a user, for authenticating a request.
     * Global by design: it runs before the request has a workspace.
     * u.workspace.id reads the foreign-key column: no join, no entity loaded.
     * A removed user has no identity, so even a token issued before the
     * removal no longer authenticates anything.
     */
    @Query("SELECT new com.queueflow.user.UserIdentity(u.id, u.workspace.id, u.role) "
            + "FROM User u WHERE u.id = :userId AND u.removedAt IS NULL")
    Optional<UserIdentity> findIdentityById(@Param("userId") UUID userId);

    /**
     * Case-insensitive name order for the UI (without it, this database's
     * collation sorts every capitalized name before every lowercase one).
     * name then breaks case-only ties ("Ada"/"ada") and id breaks
     * genuinely equal names, which users allow.
     */
    @Query("SELECT u FROM User u WHERE u.workspace.id = :workspaceId AND u.removedAt IS NULL "
            + "ORDER BY LOWER(u.name) ASC, u.name ASC, u.id ASC")
    List<User> findAllInWorkspaceSortedByName(@Param("workspaceId") UUID workspaceId);
}
