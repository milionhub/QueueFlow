package com.queueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.user.dto.UserResponse;
import com.queueflow.workspace.Workspace;

/**
 * Fast unit tests with a mocked repository - persistence behavior itself is
 * already covered by UserRepositoryTest against the real database.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    private static User persistedUser(UUID id, String name, String email, String passwordHash, UserRole role,
            UUID workspaceId, OffsetDateTime timestamp) {
        Workspace workspace = new Workspace("Acme Inc.");
        ReflectionTestUtils.setField(workspace, "id", workspaceId);

        User user = new User(name, email, passwordHash, role, workspace);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", timestamp);
        ReflectionTestUtils.setField(user, "updatedAt", timestamp);
        return user;
    }

    @Test
    void getByIdReturnsMappedResponse() {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        User user = persistedUser(id, "Ada Lovelace", "ada@example.com", "hashed-password", UserRole.ADMIN,
                workspaceId, timestamp);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(id);

        assertThat(response).isEqualTo(
                new UserResponse(id, "Ada Lovelace", "ada@example.com", UserRole.ADMIN, workspaceId, timestamp, timestamp));
    }

    @Test
    void getByIdThrowsResourceNotFoundExceptionWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getByEmailReturnsMappedResponse() {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        User user = persistedUser(id, "Grace Hopper", "grace@example.com", "hashed-password", UserRole.MEMBER,
                workspaceId, timestamp);
        when(userRepository.findByEmail("grace@example.com")).thenReturn(Optional.of(user));

        UserResponse response = userService.getByEmail("grace@example.com");

        assertThat(response).isEqualTo(
                new UserResponse(id, "Grace Hopper", "grace@example.com", UserRole.MEMBER, workspaceId, timestamp, timestamp));
    }

    @Test
    void getByEmailThrowsResourceNotFoundExceptionWhenMissing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByEmail("missing@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }

    @Test
    void userResponseNeverContainsPasswordHash() {
        // Structural guarantee: the DTO has no component for it at all.
        assertThat(UserResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("passwordHash");

        // Behavioral guarantee: the mapped value never carries the raw hash.
        UUID id = UUID.randomUUID();
        User user = persistedUser(id, "Ada Lovelace", "ada@example.com", "super-secret-hash", UserRole.ADMIN,
                UUID.randomUUID(), OffsetDateTime.now());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(id);

        assertThat(response.toString()).doesNotContain("super-secret-hash");
    }
}
