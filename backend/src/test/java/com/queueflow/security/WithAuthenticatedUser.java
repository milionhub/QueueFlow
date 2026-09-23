package com.queueflow.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import com.queueflow.user.UserRole;

/**
 * Runs a test as an authenticated caller, with exactly the Authentication
 * production creates for a valid bearer token: an {@link AuthenticatedUserToken}
 * whose principal is an {@link AuthenticatedUser}. The rest of the security
 * filter chain (authorization rules, error handling) still applies.
 *
 * For controller-focused tests, where the subject is not token cryptography.
 * Token decoding and the database lookup of the current user are covered
 * with real JWTs by the integration tests. Spring Security's own jwt() test
 * support is deliberately not used: it bypasses the application's JWT
 * converter and would produce a different principal type.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = WithAuthenticatedUser.Factory.class)
public @interface WithAuthenticatedUser {

    String USER_ID = "00000000-0000-7000-8000-000000000001";
    String WORKSPACE_ID = "00000000-0000-7000-8000-0000000000aa";

    String userId() default USER_ID;

    String workspaceId() default WORKSPACE_ID;

    UserRole role() default UserRole.ADMIN;

    final class Factory implements WithSecurityContextFactory<WithAuthenticatedUser> {

        @Override
        public SecurityContext createSecurityContext(WithAuthenticatedUser annotation) {
            AuthenticatedUser user = new AuthenticatedUser(UUID.fromString(annotation.userId()),
                    UUID.fromString(annotation.workspaceId()), annotation.role());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new AuthenticatedUserToken(user));
            return context;
        }
    }
}
