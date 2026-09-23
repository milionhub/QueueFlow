package com.queueflow.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The Spring Security Authentication for a request carrying a valid access
 * token. Its principal is the {@link AuthenticatedUser}, so controllers
 * receive it with {@code @AuthenticationPrincipal AuthenticatedUser}.
 *
 * Holds no credentials: the raw JWT is not needed once it has been verified
 * and resolved to a current user, so it is not retained (and cannot show up
 * in toString or logs). The single authority, ROLE_ADMIN or ROLE_MEMBER,
 * mirrors the user's current database role.
 */
public final class AuthenticatedUserToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser user;

    public AuthenticatedUserToken(AuthenticatedUser user) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return user.userId().toString();
    }
}
