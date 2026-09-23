package com.queueflow.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.common.exception.ForbiddenOperationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.security.WithAuthenticatedUser;
import com.queueflow.user.UserRole;
import com.queueflow.user.dto.CreateMemberRequest;
import com.queueflow.user.dto.UserResponse;

/**
 * Web-layer slice with UserService mocked. Same approach as
 * WorkspaceControllerTest: real MVC mapping, JSON serialization and the
 * real security filter chain, run as an authenticated user
 * (@WithAuthenticatedUser).
 */
@WebMvcTest({UserController.class, WorkspaceMemberController.class})
@Import(WebSecurityTestConfiguration.class)
@WithAuthenticatedUser
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** The principal @WithAuthenticatedUser installs: the only possible acting user. */
    private static final AuthenticatedUser ACTOR = new AuthenticatedUser(
            UUID.fromString(WithAuthenticatedUser.USER_ID), UUID.fromString(WithAuthenticatedUser.WORKSPACE_ID),
            UserRole.ADMIN);

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
        when(userService.getById(ACTOR, id)).thenReturn(userResponse(id, "ada@example.com", workspaceId));

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

        verify(userService).getById(ACTOR, id);
    }

    @Test
    void getByEmailReturns200WithExpectedJson() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(userService.getByEmail(ACTOR, "ada@example.com"))
                .thenReturn(userResponse(id, "ada@example.com", workspaceId));

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
        when(userService.getByEmail(ACTOR, email))
                .thenReturn(userResponse(UUID.randomUUID(), email, UUID.randomUUID()));

        mockMvc.perform(get("/api/users/by-email").param("email", email))
                .andExpect(status().isOk());

        // Also proves "/by-email" is routed to the literal mapping, never
        // captured by "/{userId}" as a (bad) UUID path variable.
        verify(userService).getByEmail(ACTOR, email);
        verifyNoMoreInteractions(userService);
    }

    // ---------------------------------------------------------------
    // LIST MEMBERS BY WORKSPACE (WorkspaceMemberController)
    // ---------------------------------------------------------------

    @Test
    void listMembersReturns200ArrayInServiceOrderWithoutPasswordHash() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID zoe = UUID.randomUUID();
        UUID ada = UUID.randomUUID();
        // Deliberately not name-sorted: the controller must not re-sort.
        when(userService.getByWorkspace(ACTOR, workspaceId)).thenReturn(List.of(
                userResponse(zoe, "zoe@example.com", workspaceId), userResponse(ada, "ada@example.com", workspaceId)));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/members", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(zoe.toString()))
                .andExpect(jsonPath("$[0].email").value("zoe@example.com"))
                .andExpect(jsonPath("$[1].id").value(ada.toString()))
                .andExpect(jsonPath("$[0].role").value("MEMBER"))
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$[*].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[*].workspace").doesNotExist());

        verify(userService).getByWorkspace(ACTOR, workspaceId);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void listMembersWithNoMembersReturns200EmptyArray() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(userService.getByWorkspace(ACTOR, workspaceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/members", workspaceId))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listMembersWithNonUuidIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/members", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).getByWorkspace(any(), any());
    }

    // ---------------------------------------------------------------
    // CREATE MEMBER
    // ---------------------------------------------------------------

    private static final String MEMBER_JSON =
            "{\"name\":\"Pedro\",\"email\":\"pedro@example.com\",\"password\":\"pedro-password\"}";

    @Test
    void createMemberReturns201WithLocationAndTheSafeUserResponse() throws Exception {
        UUID workspaceId = ACTOR.workspaceId();
        UUID id = UUID.randomUUID();
        CreateMemberRequest request = new CreateMemberRequest("Pedro", "pedro@example.com", "pedro-password");
        when(userService.createMember(ACTOR, workspaceId, request))
                .thenReturn(userResponse(id, "pedro@example.com", workspaceId));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/members", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON).content(MEMBER_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/users/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        verify(userService).createMember(ACTOR, workspaceId, request);
    }

    /** Unknown fields are ignored: role and workspace cannot even reach the service. */
    @Test
    void createMemberIgnoresRoleAndWorkspaceFieldsInTheBody() throws Exception {
        UUID workspaceId = ACTOR.workspaceId();
        when(userService.createMember(eq(ACTOR), eq(workspaceId), any()))
                .thenReturn(userResponse(UUID.randomUUID(), "pedro@example.com", workspaceId));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/members", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pedro\",\"email\":\"pedro@example.com\","
                                + "\"password\":\"pedro-password\",\"role\":\"ADMIN\",\"ROLE\":\"ADMIN\","
                                + "\"workspaceId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());

        verify(userService).createMember(ACTOR, workspaceId,
                new CreateMemberRequest("Pedro", "pedro@example.com", "pedro-password"));
    }

    @Test
    void createMemberWithInvalidBodyIs400WithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/members", ACTOR.workspaceId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"email\":\"pedro@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userService, never()).createMember(any(), any(), any());
    }

    @Test
    void createMemberMapsTheServiceDecisionsToTheErrorContract() throws Exception {
        UUID workspaceId = ACTOR.workspaceId();
        when(userService.createMember(eq(ACTOR), eq(workspaceId), any()))
                .thenThrow(new ForbiddenOperationException("Only workspace admins can create members"))
                .thenThrow(new ResourceNotFoundException("Workspace not found: " + workspaceId))
                .thenThrow(new ResourceAlreadyExistsException("Email is already registered"));

        for (int status : new int[] {403, 404, 409}) {
            mockMvc.perform(post("/api/workspaces/{workspaceId}/members", workspaceId)
                            .contentType(MediaType.APPLICATION_JSON).content(MEMBER_JSON))
                    .andExpect(status().is(status))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(status))
                    .andExpect(jsonPath("$.path").value("/api/workspaces/" + workspaceId + "/members"));
        }
    }
}
