package com.queueflow.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Global lookup, for login only: at that point there is no workspace yet.
     * Domain code uses the workspace-scoped lookups below.
     */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Tenant-scoped: a user of another workspace is simply not found. */
    Optional<User> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Tenant-scoped: emails of other workspaces cannot be probed. */
    Optional<User> findByEmailAndWorkspaceId(String email, UUID workspaceId);

    /**
     * Current id, workspace and role of a user, for authenticating a request.
     * Global by design: it runs before the request has a workspace.
     * u.workspace.id reads the foreign-key column: no join, no entity loaded.
     */
    @Query("SELECT new com.queueflow.user.UserIdentity(u.id, u.workspace.id, u.role) "
            + "FROM User u WHERE u.id = :userId")
    Optional<UserIdentity> findIdentityById(@Param("userId") UUID userId);

    /**
     * Case-insensitive name order for the UI (without it, this database's
     * collation sorts every capitalized name before every lowercase one).
     * name then breaks case-only ties ("Ada"/"ada") and id breaks
     * genuinely equal names, which users allow.
     */
    @Query("SELECT u FROM User u WHERE u.workspace.id = :workspaceId ORDER BY LOWER(u.name) ASC, u.name ASC, u.id ASC")
    List<User> findAllInWorkspaceSortedByName(@Param("workspaceId") UUID workspaceId);
}
