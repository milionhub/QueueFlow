package com.queueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

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
}
