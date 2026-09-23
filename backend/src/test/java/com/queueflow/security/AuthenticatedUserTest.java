package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.queueflow.user.UserRole;

class AuthenticatedUserTest {

    @Test
    void requiresAllThreeComponents() {
        UUID id = UUID.randomUUID();

        assertThatNullPointerException().isThrownBy(() -> new AuthenticatedUser(null, id, UserRole.ADMIN));
        assertThatNullPointerException().isThrownBy(() -> new AuthenticatedUser(id, null, UserRole.ADMIN));
        assertThatNullPointerException().isThrownBy(() -> new AuthenticatedUser(id, id, null));
    }

    @Test
    void knowsWhetherItIsAnAdmin() {
        UUID id = UUID.randomUUID();

        assertThat(new AuthenticatedUser(id, id, UserRole.ADMIN).isAdmin()).isTrue();
        assertThat(new AuthenticatedUser(id, id, UserRole.MEMBER).isAdmin()).isFalse();
    }
}
