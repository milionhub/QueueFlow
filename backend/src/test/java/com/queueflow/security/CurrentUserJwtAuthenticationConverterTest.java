package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.queueflow.user.UserIdentity;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;

/**
 * JWT (already verified by the decoder) to current user. The token only
 * contributes the subject; everything else comes from the database.
 */
class CurrentUserJwtAuthenticationConverterTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CurrentUserJwtAuthenticationConverter converter =
            new CurrentUserJwtAuthenticationConverter(userRepository);

    private static Jwt.Builder jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("header.payload.signature")
                .header("alg", "HS256")
                .issuer("queueflow")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));
    }

    @Test
    void resolvesTheSubjectToTheCurrentUserFromTheDatabase() {
        when(userRepository.findIdentityById(USER_ID))
                .thenReturn(Optional.of(new UserIdentity(USER_ID, WORKSPACE_ID, UserRole.MEMBER)));

        AuthenticatedUserToken authentication = converter.convert(jwt().subject(USER_ID.toString()).build());

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedUser(USER_ID, WORKSPACE_ID, UserRole.MEMBER));
        assertThat(authentication.getName()).isEqualTo(USER_ID.toString());
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBER");
    }

    /** Claims a token might carry (it never does, by design) cannot override the database. */
    @Test
    void ignoresRoleAndWorkspaceClaimsInTheToken() {
        when(userRepository.findIdentityById(USER_ID))
                .thenReturn(Optional.of(new UserIdentity(USER_ID, WORKSPACE_ID, UserRole.MEMBER)));

        AuthenticatedUserToken authentication = converter.convert(jwt().subject(USER_ID.toString())
                .claim("role", "ADMIN")
                .claim("roles", "ADMIN")
                .claim("workspaceId", UUID.randomUUID().toString())
                .claim("scope", "admin")
                .build());

        assertThat(authentication.getPrincipal().role()).isEqualTo(UserRole.MEMBER);
        assertThat(authentication.getPrincipal().workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBER");
    }

    @Test
    void keepsNeitherTheTokenNorAnyCredential() {
        when(userRepository.findIdentityById(USER_ID))
                .thenReturn(Optional.of(new UserIdentity(USER_ID, WORKSPACE_ID, UserRole.ADMIN)));

        AuthenticatedUserToken authentication = converter.convert(jwt().subject(USER_ID.toString()).build());

        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.toString()).doesNotContain("header.payload.signature");
    }

    @Test
    void unknownOrDeletedUserIsAnInvalidToken() {
        when(userRepository.findIdentityById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(jwt().subject(USER_ID.toString()).build()))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void missingOrNonUuidSubjectIsAnInvalidTokenWithoutADatabaseLookup() {
        assertThatThrownBy(() -> converter.convert(jwt().claim("other", "x").build()))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(jwt().subject("ada@example.com").build()))
                .isInstanceOf(InvalidBearerTokenException.class);

        verify(userRepository, never()).findIdentityById(any());
    }
}
