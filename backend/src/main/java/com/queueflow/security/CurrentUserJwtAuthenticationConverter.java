package com.queueflow.security;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.queueflow.user.UserIdentity;
import com.queueflow.user.UserRepository;

/**
 * The last step of bearer authentication: turns an already verified JWT
 * (signature, issuer, exp) into the current user. The token only says who
 * the caller is (sub); workspace and role are read from the database on
 * every request, so a changed role or a deleted user takes effect on the
 * next request instead of when the token expires.
 *
 * Any failure - no sub, a sub that is not a UUID, a user that does not (or
 * no longer) exist - is the same invalid-token 401, so the response never
 * says which.
 *
 * Deliberately not a Spring bean: Spring Boot adds every Converter bean to
 * Spring MVC's conversion service, where this one does not belong.
 * SecurityConfig creates it.
 */
public class CurrentUserJwtAuthenticationConverter implements Converter<Jwt, AuthenticatedUserToken> {

    private final UserRepository userRepository;

    public CurrentUserJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AuthenticatedUserToken convert(Jwt jwt) {
        UserIdentity identity = userRepository.findIdentityById(userId(jwt))
                .orElseThrow(CurrentUserJwtAuthenticationConverter::invalidToken);
        return new AuthenticatedUserToken(
                new AuthenticatedUser(identity.id(), identity.workspaceId(), identity.role()));
    }

    private static UUID userId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw invalidToken();
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException notAUuid) {
            throw invalidToken();
        }
    }

    /** An AuthenticationException, so the bearer filter answers with the entry point's 401. */
    private static InvalidBearerTokenException invalidToken() {
        return new InvalidBearerTokenException("Invalid or expired token");
    }
}
