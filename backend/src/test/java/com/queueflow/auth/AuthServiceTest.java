package com.queueflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.auth.dto.AuthResponse;
import com.queueflow.auth.dto.LoginRequest;
import com.queueflow.auth.dto.RegisterRequest;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.InvalidCredentialsException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.security.AccessTokenService;
import com.queueflow.security.IssuedAccessToken;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * AuthService with mocked repositories and token issuing, but the real
 * production PasswordEncoder (spied, to observe the password checks), so
 * hashing and matching are genuine BCrypt.
 */
class AuthServiceTest {

    private static final String PASSWORD = "correct horse battery";
    private static final IssuedAccessToken TOKEN = new IssuedAccessToken("issued.jwt.value", 3600);
    /** A two-byte UTF-8 character. */
    private static final String E_ACUTE = "é";
    /** A character outside the BMP: two UTF-16 units, four UTF-8 bytes. */
    private static final String EMOJI = "😀";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private final PasswordEncoder passwordEncoder = spy(PasswordEncoderFactories.createDelegatingPasswordEncoder());

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, workspaceRepository, passwordEncoder, accessTokenService);
        clearInvocations(passwordEncoder); // the constructor encodes the unknown-user dummy once
        when(accessTokenService.issue(any())).thenReturn(TOKEN);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(userRepository.save(any(User.class))).thenAnswer(call -> withId(call.getArgument(0)));
    }

    // ------------------------------------------------------------------
    // register
    // ------------------------------------------------------------------

    @Test
    void registerCreatesWorkspaceAndAdminWithNormalizedValuesAndHashedPassword() {
        AuthResponse response = authService.register(
                new RegisterRequest("  Ada Lovelace ", "  Ada@Example.COM ", PASSWORD, "  Acme Inc. "));

        ArgumentCaptor<Workspace> workspace = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(workspace.capture());
        assertThat(workspace.getValue().getName()).isEqualTo("Acme Inc.");

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        User saved = user.getValue();
        assertThat(saved.getName()).isEqualTo("Ada Lovelace");
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getWorkspace()).isSameAs(workspace.getValue());
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();

        verify(accessTokenService).issue(saved.getId());
        assertThat(response.accessToken()).isEqualTo("issued.jwt.value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.user().id()).isEqualTo(saved.getId());
        assertThat(response.user().email()).isEqualTo("ada@example.com");
        assertThat(response.user().role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.user().workspaceId()).isEqualTo(workspace.getValue().getId());
    }

    @Test
    void registerChecksUniquenessOnTheNormalizedEmail() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Ada", " ADA@example.com", PASSWORD, "Acme")))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessage("Email is already registered");

        verify(workspaceRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(accessTokenService);
    }

    @Test
    void registerNeverTrimsOrChangesThePassword() {
        String padded = "  Pass Word  ";

        authService.register(new RegisterRequest("Ada", "ada@example.com", padded, "Acme"));

        String hash = savedUser().getPasswordHash();
        assertThat(passwordEncoder.matches(padded, hash)).isTrue();
        assertThat(passwordEncoder.matches(padded.strip(), hash)).isFalse();
    }

    @Test
    void passwordLimitIs72Utf8BytesNotCharacters() {
        String ascii72 = "a".repeat(72);
        String multibyte72 = E_ACUTE.repeat(36); // 36 characters, 72 bytes

        authService.register(new RegisterRequest("Ada", "ada1@example.com", ascii72, "Acme"));
        authService.register(new RegisterRequest("Ada", "ada2@example.com", multibyte72, "Acme"));

        assertThat(multibyte72.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void passwordOver72Utf8BytesIsRejectedBeforeHashing() {
        String ascii73 = "a".repeat(73);
        String multibyte73 = E_ACUTE.repeat(36) + "a"; // 37 characters, 73 bytes

        for (String password : new String[] {ascii73, multibyte73}) {
            assertThatThrownBy(() -> authService.register(
                    new RegisterRequest("Ada", "ada@example.com", password, "Acme")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("password must be at most 72 bytes when UTF-8 encoded")
                    .hasMessageNotContaining(password);
        }
        verify(passwordEncoder, never()).encode(anyString());
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void passwordMinimumCountsCharactersNotUtf16Units() {
        String fourEmoji = EMOJI.repeat(4); // 8 UTF-16 units, 4 characters

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Ada", "ada@example.com", fourEmoji, "Acme")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("password must be at least 8 characters");

        authService.register(new RegisterRequest("Ada", "ada@example.com", "8 chars!", "Acme"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void namesMustNotBeBlankAfterTrimming() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest("   ", "ada@example.com", PASSWORD, "Acme")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessage("name must not be blank");
        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ada", "ada@example.com", PASSWORD, "  ")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessage("workspaceName must not be blank");
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void nameLengthIsCheckedAfterTrimming() {
        String max = "n".repeat(255);

        authService.register(new RegisterRequest("  " + max + "  ", "ada@example.com", PASSWORD, "  " + max + " "));
        assertThat(savedUser().getName()).isEqualTo(max);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest(max + "n", "ada@example.com", PASSWORD, "Acme")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessage("name must be at most 255 characters");
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Ada", "ada@example.com", PASSWORD, max + "n")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("workspaceName must be at most 255 characters");
    }

    @Test
    void emailLengthIsCheckedAfterNormalization() {
        // 321 characters; the second call shows surrounding whitespace does not count.
        String tooLong = "a".repeat(309) + "@example.com";

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ada", tooLong, PASSWORD, "Acme")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessage("email must be at most 320 characters");

        authService.register(new RegisterRequest("Ada", "  " + tooLong.substring(1) + "  ", PASSWORD, "Acme"));
        assertThat(savedUser().getEmail()).hasSize(320);
    }

    @Test
    void emailSyntaxIsCheckedOnTheNormalizedValue() {
        for (String invalid : new String[] {"not-an-email", "ada@", "@example.com", "ada@example", "a da@example.com",
                "ada@@example.com", "ada@example..com"}) {
            assertThatThrownBy(() -> authService.register(new RegisterRequest("Ada", invalid, PASSWORD, "Acme")))
                    .as(invalid)
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("email must be a valid email address");
        }
        verify(workspaceRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    @Test
    void loginWithCorrectPasswordIssuesTokenForThatUser() {
        User ada = existingUser("ada@example.com", passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(ada));

        AuthResponse response = authService.login(new LoginRequest("  ADA@Example.com ", PASSWORD));

        verify(userRepository).findByEmail("ada@example.com");
        verify(accessTokenService).issue(ada.getId());
        assertThat(response.accessToken()).isEqualTo("issued.jwt.value");
        assertThat(response.user().id()).isEqualTo(ada.getId());
    }

    @Test
    void wrongPasswordAndUnknownEmailFailIdentically() {
        User ada = existingUser("ada@example.com", passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(ada));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        Throwable wrongPassword = catchLoginFailure(new LoginRequest("ada@example.com", "wrong password"));
        Throwable unknownEmail = catchLoginFailure(new LoginRequest("nobody@example.com", PASSWORD));

        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(wrongPassword.getMessage());
        verifyNoInteractions(accessTokenService);
    }

    /** The timing mitigation: an unknown email still costs one real BCrypt check. */
    @Test
    void unknownEmailStillRunsOnePasswordCheckAgainstAValidDummyHash() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        catchLoginFailure(new LoginRequest("nobody@example.com", PASSWORD));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).matches(eq(PASSWORD), hash.capture());
        assertThat(hash.getValue()).startsWith("{bcrypt}$2");
        verify(passwordEncoder, never()).encode(anyString()); // encoded once, at construction
    }

    @Test
    void unusableStoredHashIsTheSameGenericFailure() {
        String[] unusable = {"dummy-bcrypt-like-not-a-real-hash", "{unknown}abc", "{bcrypt}garbage"};
        for (String storedHash : unusable) {
            when(userRepository.findByEmail("legacy@example.com"))
                    .thenReturn(Optional.of(existingUser("legacy@example.com", storedHash)));

            assertThat(catchLoginFailure(new LoginRequest("legacy@example.com", PASSWORD)))
                    .isExactlyInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password")
                    .hasNoCause();
        }
        verifyNoInteractions(accessTokenService);
    }

    @Test
    void loginTreatsPasswordsOver72BytesAsJustWrong() {
        User ada = existingUser("ada@example.com", passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(ada));

        assertThat(catchLoginFailure(new LoginRequest("ada@example.com", "x".repeat(100))))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ------------------------------------------------------------------

    private Throwable catchLoginFailure(LoginRequest request) {
        return catchThrowable(() -> authService.login(request));
    }

    private User savedUser() {
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(user.capture());
        return user.getValue();
    }

    private static User existingUser(String email, String passwordHash) {
        Workspace workspace = withId(new Workspace("Acme"));
        return withId(new User("Ada", email, passwordHash, UserRole.MEMBER, workspace));
    }

    /** What the database does on insert: the id is generated. */
    private static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
