package com.queueflow.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Current id, workspace and role of a user, for authenticating a request.
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
