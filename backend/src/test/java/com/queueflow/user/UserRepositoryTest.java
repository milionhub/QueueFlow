package com.queueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void userIsPersistedReferencingItsWorkspaceWithRoleStoredAsString() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        User user = userRepository.saveAndFlush(
                new User("Ada Lovelace", "ada@example.com", "hashed-password", UserRole.ADMIN, workspace));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getWorkspace().getId()).isEqualTo(workspace.getId());

        entityManager.clear();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(reloaded.getWorkspace().getName()).isEqualTo("Acme Inc.");

        String rawRole = (String) entityManager.getEntityManager()
                .createNativeQuery("SELECT role FROM users WHERE id = ?1")
                .setParameter(1, user.getId())
                .getSingleResult();
        assertThat(rawRole).isEqualTo("ADMIN");
    }

    @Test
    void findByEmailReturnsPersistedUser() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Globex"));
        userRepository.saveAndFlush(
                new User("Grace Hopper", "grace@example.com", "hashed-password", UserRole.MEMBER, workspace));

        Optional<User> found = userRepository.findByEmail("grace@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Grace Hopper");
        assertThat(found.get().getRole()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void findByEmailReturnsEmptyWhenNoUserMatches() {
        assertThat(userRepository.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void existsByEmailReflectsPersistedState() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Initech"));
        userRepository.saveAndFlush(
                new User("Peter Gibbons", "peter@example.com", "hashed-password", UserRole.MEMBER, workspace));

        assertThat(userRepository.existsByEmail("peter@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void duplicateEmailIsRejectedByDatabaseConstraint() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Umbrella Corp"));
        userRepository.saveAndFlush(
                new User("First User", "duplicate@example.com", "hashed-password", UserRole.MEMBER, workspace));

        User duplicate = new User("Second User", "duplicate@example.com", "hashed-password", UserRole.ADMIN, workspace);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** No-op UPDATE: writes a new row version, moving the row to the end of heap/index order. */
    private void rewriteRow(UUID userId) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE users SET name = name WHERE id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
    }

    private User member(Workspace workspace, String name, String email) {
        return userRepository.saveAndFlush(new User(name, email, "hashed-password", UserRole.MEMBER, workspace));
    }


    /**
     * Asks PostgreSQL itself whether a sorts before b under the database's
     * own collation - the rule the secondary "name ASC" key uses. Keeps the
     * case-only-tie expectation exact without hard-coding a collation
     * (musl/alpine sorts "Alpha" before "alpha"; glibc may not).
     */
    private boolean sortsBefore(String a, String b) {
        return (Boolean) entityManager.getEntityManager()
                .createNativeQuery("SELECT CAST(?1 AS text) < CAST(?2 AS text)")
                .setParameter(1, a)
                .setParameter(2, b)
                .getSingleResult();
    }

    @Test
    void workspaceListReturnsOnlyThatWorkspacesMembersOrderedCaseInsensitivelyByName() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Workspace other = workspaceRepository.saveAndFlush(new Workspace("Globex"));
        // Mixed case on purpose: a case-sensitive (byte-order) sort would put
        // "ada" and "bob" after every capitalized name.
        User zoe = member(workspace, "Zoe", "zoe@example.com");
        User ada = member(workspace, "ada", "ada-list@example.com");
        User mia = member(workspace, "Mia", "mia@example.com");
        User bob = member(workspace, "bob", "bob@example.com");
        member(other, "Aaron", "aaron@example.com");
        entityManager.clear();

        List<User> members = userRepository.findAllInWorkspaceSortedByName(workspace.getId());

        assertThat(members).extracting(User::getName).containsExactly("ada", "bob", "Mia", "Zoe");
        assertThat(members).extracting(User::getId)
                .containsExactly(ada.getId(), bob.getId(), mia.getId(), zoe.getId());
    }

    @Test
    void workspaceListBreaksCaseOnlyTiesByNameBeforeId() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        String earlier = sortsBefore("Ada", "ada") ? "Ada" : "ada";
        String later = earlier.equals("Ada") ? "ada" : "Ada";
        // Insert the later-sorting spelling FIRST, so it gets the smaller id:
        // id order and name order now disagree, and only the "name" key can
        // produce the expected result.
        User laterUser = member(workspace, later, "ada-later@example.com");
        User ben = member(workspace, "Ben", "ben@example.com");
        User earlierUser = member(workspace, earlier, "ada-earlier@example.com");
        entityManager.clear();

        assertThat(userRepository.findAllInWorkspaceSortedByName(workspace.getId()))
                .extracting(User::getId)
                .containsExactly(earlierUser.getId(), laterUser.getId(), ben.getId());
    }

    @Test
    void workspaceListBreaksEqualNamesByIdAscending() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User first = member(workspace, "Sam", "sam1@example.com");
        User second = member(workspace, "Sam", "sam2@example.com");
        User third = member(workspace, "Sam", "sam3@example.com");
        // uuidv7 ids ascend in insertion order (lowercase-hex comparison
        // matches PostgreSQL uuid ordering).
        assertThat(List.of(first, second, third)).extracting(u -> u.getId().toString()).isSorted();

        // Reverse the physical order so only the id tie-breaker can yield id order.
        rewriteRow(third.getId());
        rewriteRow(second.getId());
        rewriteRow(first.getId());
        entityManager.clear();

        assertThat(userRepository.findAllInWorkspaceSortedByName(workspace.getId()))
                .extracting(User::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void workspaceListIsEmptyForWorkspaceWithoutMembers() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        assertThat(userRepository.findAllInWorkspaceSortedByName(workspace.getId())).isEmpty();
    }
}
