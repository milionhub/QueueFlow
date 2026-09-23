package com.queueflow.user;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.config.SecurityConfig;
import com.queueflow.user.dto.UserResponse;

/**
 * Web-layer slice with UserService mocked. Same approach as
 * WorkspaceControllerTest: real MVC mapping, JSON serialization and the
 * real (temporary) SecurityConfig.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static UserResponse userResponse(UUID id, String email, UUID workspaceId) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        return new UserResponse(id, "Ada Lovelace", email, UserRole.MEMBER, workspaceId, timestamp, timestamp);
    }

    @Test
    void getByIdReturns200WithExpectedJson() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(userService.getById(id)).thenReturn(userResponse(id, "ada@example.com", workspaceId));

        mockMvc.perform(get("/api/users/{userId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService).getById(id);
    }

    @Test
    void getByEmailReturns200WithExpectedJson() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(userService.getByEmail("ada@example.com")).thenReturn(userResponse(id, "ada@example.com", workspaceId));

        mockMvc.perform(get("/api/users/by-email").param("email", "ada@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getByEmailPassesExactEmailToService() throws Exception {
        // Mixed case and a plus-tag: the controller must not normalize,
        // trim or otherwise rewrite the value (that is a service/Phase 2
        // concern), and "+" must survive query-string decoding.
        String email = "Ada.Lovelace+queue@Example.com";
        when(userService.getByEmail(email)).thenReturn(userResponse(UUID.randomUUID(), email, UUID.randomUUID()));

        mockMvc.perform(get("/api/users/by-email").param("email", email))
                .andExpect(status().isOk());

        // Also proves "/by-email" is routed to the literal mapping, never
        // captured by "/{userId}" as a (bad) UUID path variable.
        verify(userService).getByEmail(email);
        verifyNoMoreInteractions(userService);
    }
}
