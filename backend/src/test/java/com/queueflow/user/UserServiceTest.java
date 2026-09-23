package com.queueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ForbiddenOperationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.user.dto.CreateMemberRequest;
import com.queueflow.user.dto.UserResponse;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Fast unit tests with mocked repositories - persistence behavior itself is
 * already covered by UserRepositoryTest against the real database.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    /** The caller of every operation below; its workspace is the only one the service can see. */
    private static final AuthenticatedUser ACTOR =
            new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UserRole.MEMBER);

    /** An ADMIN of the same workspace as ACTOR (who is a MEMBER). */
    private static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(UUID.randomUUID(), ACTOR.workspaceId(), UserRole.ADMIN);

    private static final String PASSWORD = "  pedro's password  ";

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    /** The real production encoder: member passwords must be genuine BCrypt. */
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, workspaceRepository, passwordEncoder);
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
        UUID workspaceId = ACTOR.workspaceId();
        OffsetDateTime timestamp = OffsetDateTime.now();
        User user = persistedUser(id, "Ada Lovelace", "ada@example.com", "hashed-password", UserRole.ADMIN,
                workspaceId, timestamp);
        when(userRepository.findByIdAndWorkspaceId(id, ACTOR.workspaceId())).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(ACTOR, id);

        assertThat(response).isEqualTo(
                new UserResponse(id, "Ada Lovelace", "ada@example.com", UserRole.ADMIN, workspaceId, timestamp, timestamp));
    }

    @Test
    void getByIdThrowsResourceNotFoundExceptionWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByIdAndWorkspaceId(id, ACTOR.workspaceId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(ACTOR, id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getByEmailReturnsMappedResponse() {
        UUID id = UUID.randomUUID();
        UUID workspaceId = ACTOR.workspaceId();
        OffsetDateTime timestamp = OffsetDateTime.now();
        User user = persistedUser(id, "Grace Hopper", "grace@example.com", "hashed-password", UserRole.MEMBER,
                workspaceId, timestamp);
        when(userRepository.findByEmailAndWorkspaceId("grace@example.com", ACTOR.workspaceId()))
                .thenReturn(Optional.of(user));

        UserResponse response = userService.getByEmail(ACTOR, "grace@example.com");

        assertThat(response).isEqualTo(
                new UserResponse(id, "Grace Hopper", "grace@example.com", UserRole.MEMBER, workspaceId, timestamp, timestamp));
    }

    @Test
    void getByEmailThrowsResourceNotFoundExceptionWhenMissing() {
        when(userRepository.findByEmailAndWorkspaceId("missing@example.com", ACTOR.workspaceId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByEmail(ACTOR, "missing@example.com"))
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
        when(userRepository.findByIdAndWorkspaceId(id, ACTOR.workspaceId())).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(ACTOR, id);

        assertThat(response.toString()).doesNotContain("super-secret-hash");
    }

    // ---------------------------------------------------------------
    // LIST MEMBERS BY WORKSPACE
    // ---------------------------------------------------------------

    @Test
    void getByWorkspaceReturnsMappedMembersPreservingRepositoryOrderWithoutPasswordHash() {
        UUID workspaceId = ACTOR.workspaceId();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        User zoe = persistedUser(UUID.randomUUID(), "Zoe", "zoe@example.com", "hash-zoe", UserRole.ADMIN,
                workspaceId, timestamp);
        User ada = persistedUser(UUID.randomUUID(), "Ada", "ada@example.com", "hash-ada", UserRole.MEMBER,
                workspaceId, timestamp);
        // Deliberately not name-sorted: the service must not re-sort.
        when(userRepository.findAllInWorkspaceSortedByName(workspaceId)).thenReturn(List.of(zoe, ada));

        List<UserResponse> responses = userService.getByWorkspace(ACTOR, workspaceId);

        assertThat(responses).extracting(UserResponse::id).containsExactly(zoe.getId(), ada.getId());
        assertThat(responses.get(0)).isEqualTo(new UserResponse(zoe.getId(), "Zoe", "zoe@example.com",
                UserRole.ADMIN, workspaceId, timestamp, timestamp));
    }

    @Test
    void getByWorkspaceReturnsEmptyListForExistingWorkspaceWithoutMembers() {
        UUID workspaceId = ACTOR.workspaceId();
        when(userRepository.findAllInWorkspaceSortedByName(workspaceId)).thenReturn(List.of());

        assertThat(userService.getByWorkspace(ACTOR, workspaceId)).isEmpty();
    }

    @Test
    void getByWorkspaceOfAnyOtherWorkspaceIsNotFoundWithoutQueryingMembers() {
        UUID otherWorkspaceId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.getByWorkspace(ACTOR, otherWorkspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Workspace not found: " + otherWorkspaceId);

        verify(userRepository, never()).findAllInWorkspaceSortedByName(any());
    }

    // ---------------------------------------------------------------
    // CREATE MEMBER
    // ---------------------------------------------------------------

    @Test
    void createMemberAddsAMemberToTheAdminsWorkspaceWithRegistrationRulesAndBcrypt() {
        Workspace workspace = new Workspace("Acme Inc.");
        ReflectionTestUtils.setField(workspace, "id", ADMIN.workspaceId());
        when(userRepository.existsByEmail("pedro@example.com")).thenReturn(false);
        when(workspaceRepository.getReferenceById(ADMIN.workspaceId())).thenReturn(workspace);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse response = userService.createMember(ADMIN, ADMIN.workspaceId(),
                new CreateMemberRequest("  Pedro  ", "  Pedro@Example.COM ", PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User member = saved.getValue();
        assertThat(member.getName()).isEqualTo("Pedro");
        assertThat(member.getEmail()).isEqualTo("pedro@example.com");
        assertThat(member.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(member.getWorkspace().getId()).isEqualTo(ADMIN.workspaceId());
        assertThat(member.getPasswordHash()).startsWith("{bcrypt}");
        // Never trimmed: only the exact password matches.
        assertThat(passwordEncoder.matches(PASSWORD, member.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD.strip(), member.getPasswordHash())).isFalse();
        assertThat(response.role()).isEqualTo(UserRole.MEMBER);
        assertThat(response.toString()).doesNotContain(member.getPasswordHash(), PASSWORD);
    }

    @Test
    void aMemberCannotCreateMembersInTheirOwnWorkspace() {
        assertThatThrownBy(() -> userService.createMember(ACTOR, ACTOR.workspaceId(),
                new CreateMemberRequest("Pedro", "pedro@example.com", PASSWORD)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Only workspace admins can create members");

        verifyNoInteractions(userRepository, workspaceRepository);
    }

    /** Another workspace is 404 before the role is looked at - for an ADMIN and a MEMBER alike. */
    @Test
    void anotherWorkspaceIsNotFoundForEveryRoleWithoutTouchingTheDatabase() {
        UUID otherWorkspaceId = UUID.randomUUID();
        CreateMemberRequest request = new CreateMemberRequest("Pedro", "pedro@example.com", PASSWORD);

        for (AuthenticatedUser caller : List.of(ADMIN, ACTOR)) {
            assertThatThrownBy(() -> userService.createMember(caller, otherWorkspaceId, request))
                    .as(caller.role().name())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Workspace not found: " + otherWorkspaceId);
        }
        verifyNoInteractions(userRepository, workspaceRepository);
    }

    @Test
    void anEmailRegisteredInAnyLetterCaseAndAnyWorkspaceIsAConflict() {
        when(userRepository.existsByEmail("pedro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createMember(ADMIN, ADMIN.workspaceId(),
                new CreateMemberRequest("Pedro", "PEDRO@EXAMPLE.COM", PASSWORD)))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createMemberAppliesTheRegistrationValidationRules() {
        assertRejected(new CreateMemberRequest("   ", "pedro@example.com", PASSWORD), "name must not be blank");
        assertRejected(new CreateMemberRequest("x".repeat(256), "pedro@example.com", PASSWORD),
                "name must be at most 255 characters");
        assertRejected(new CreateMemberRequest("Pedro", "not-an-email", PASSWORD),
                "email must be a valid email address");
        // Seven code points, fourteen UTF-16 units.
        assertRejected(new CreateMemberRequest("Pedro", "pedro@example.com", "\uD83D\uDE00".repeat(7)),
                "password must be at least 8 characters");
        assertRejected(new CreateMemberRequest("Pedro", "pedro@example.com", "\u00E9".repeat(37)),
                "password must be at most 72 bytes when UTF-8 encoded");

        verify(userRepository, never()).save(any());
    }

    private void assertRejected(CreateMemberRequest request, String message) {
        assertThatThrownBy(() -> userService.createMember(ADMIN, ADMIN.workspaceId(), request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage(message);
    }

    @Test
    void createMemberRequestNeverPrintsThePassword() {
        assertThat(new CreateMemberRequest("Pedro", "pedro@example.com", PASSWORD).toString())
                .doesNotContain(PASSWORD).contains("<redacted>");
    }
}
