package com.queueflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * Stable invariants of the generated OpenAPI document, fetched through the
 * real security filter chain. Deliberately not a snapshot: wording, ordering
 * and Springdoc's formatting may change freely; these assertions should only
 * fail when the documented contract itself changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    private String json;
    private DocumentContext doc;

    @BeforeEach
    void fetchDocument() throws Exception {
        json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        doc = JsonPath.parse(json);
    }

    @Test
    void describesTheQueueFlowApi() {
        assertThat(doc.read("$.info.title", String.class)).isEqualTo("QueueFlow API");
        assertThat(doc.read("$.info.version", String.class)).isEqualTo("v1");
        List<String> tags = doc.read("$.tags[*].name");
        assertThat(tags).containsExactlyInAnyOrder(
                "Authentication", "Workspaces", "Users", "Projects", "Tickets", "Labels", "Comments", "Activity");
    }

    @Test
    void documentsEveryApiOperationAndNothingElse() {
        Map<String, Object> paths = doc.read("$.paths");
        assertThat(paths.keySet()).allMatch(path -> path.startsWith("/api/"));
        List<String> operationIds = doc.read("$.paths.*.*.operationId");
        assertThat(operationIds).hasSize(29).doesNotHaveDuplicates();
    }

    @Test
    void documentsRepresentativeOperations() {
        assertThat(doc.read("$.paths['/api/tickets'].post.operationId", String.class)).isEqualTo("createTicket");
        assertThat(doc.read("$.paths['/api/tickets'].post.responses['201'].content['application/json'].schema.$ref",
                String.class)).isEqualTo("#/components/schemas/TicketResponse");

        assertThat(doc.read("$.paths['/api/tickets/{ticketId}'].patch.responses['403'].$ref", String.class))
                .isEqualTo(OpenApiConfig.FORBIDDEN);
        assertThat(doc.read("$.paths['/api/projects'].post.responses['409'].$ref", String.class))
                .isEqualTo(OpenApiConfig.CONFLICT);

        Map<String, Object> deleteResponses = doc.read("$.paths['/api/comments/{commentId}'].delete.responses");
        assertThat(deleteResponses).containsKey("204").doesNotContainKey("200");

        assertThat(doc.read("$.paths['/api/tickets/{ticketId}/activities'].get.responses['200']"
                + ".content['application/json'].schema.items.$ref", String.class))
                .isEqualTo("#/components/schemas/ActivityResponse");
    }

    @Test
    void documentsEnumValues() {
        assertThat(enumValues("TicketResponse", "status"))
                .containsExactly("BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE");
        assertThat(enumValues("TicketResponse", "priority"))
                .containsExactly("LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(enumValues("UserResponse", "role")).containsExactly("ADMIN", "MEMBER");
        assertThat(enumValues("ActivityResponse", "type")).containsExactly(
                "TICKET_CREATED", "STATUS_CHANGED", "PRIORITY_CHANGED", "ASSIGNEE_CHANGED",
                "TITLE_CHANGED", "DESCRIPTION_CHANGED", "LABEL_ADDED", "LABEL_REMOVED");
    }

    @Test
    void documentsPatchSemanticsWithoutLeakingPatchField() {
        assertThat(doc.read("$.components.schemas.UpdateTicketRequest.properties.assigneeId.type", List.class))
                .containsExactlyInAnyOrder("string", "null");
        assertThat(doc.read("$.components.schemas.UpdateProjectRequest.properties.description.type", List.class))
                .containsExactlyInAnyOrder("string", "null");
        assertThat(json).doesNotContain("PatchField");
    }

    @Test
    void documentsTheAuthEndpoints() {
        assertThat(doc.read("$.paths['/api/auth/register'].post.operationId", String.class)).isEqualTo("register");
        assertThat(doc.read("$.paths['/api/auth/register'].post.responses['201'].content['application/json']"
                + ".schema.$ref", String.class)).isEqualTo("#/components/schemas/AuthResponse");
        assertThat(doc.read("$.paths['/api/auth/register'].post.responses['409'].$ref", String.class))
                .isEqualTo(OpenApiConfig.CONFLICT);
        assertThat(doc.read("$.paths['/api/auth/login'].post.operationId", String.class)).isEqualTo("login");
        assertThat(doc.read("$.paths['/api/auth/login'].post.responses['401'].$ref", String.class))
                .isEqualTo(OpenApiConfig.UNAUTHORIZED);

        assertThat(doc.read("$.paths['/api/auth/me'].get.operationId", String.class)).isEqualTo("getCurrentUser");
        assertThat(doc.read("$.paths['/api/auth/me'].get.responses['200'].content['application/json'].schema.$ref",
                String.class)).isEqualTo("#/components/schemas/UserResponse");

        Map<String, Object> paths = doc.read("$.paths");
        assertThat(paths).doesNotContainKey("/api/workspaces");
    }

    @Test
    void documentsBearerAuthenticationForEverythingButRegisterAndLogin() {
        Map<String, Object> scheme = doc.read("$.components.securitySchemes.bearerAuth");
        assertThat(scheme).containsEntry("type", "http").containsEntry("scheme", "bearer")
                .containsEntry("bearerFormat", "JWT");
        assertThat(doc.read("$.security[*].bearerAuth", List.class)).hasSize(1);

        // Public: an explicit empty requirement overrides the global one.
        assertThat(doc.read("$.paths['/api/auth/register'].post.security", List.class)).isEmpty();
        assertThat(doc.read("$.paths['/api/auth/login'].post.security", List.class)).isEmpty();

        // Everything else inherits bearerAuth and documents the 401.
        List<Map<String, Object>> operations = doc.read("$.paths.*.*");
        List<Map<String, Object>> protectedOperations = operations.stream()
                .filter(operation -> !operation.containsKey("security"))
                .toList();
        assertThat(protectedOperations).hasSize(27);
        assertThat(protectedOperations).allSatisfy(operation ->
                assertThat(JsonPath.<String>read(operation, "$.responses['401'].$ref"))
                        .isEqualTo(OpenApiConfig.UNAUTHORIZED));
    }

    /** The authenticated principal is resolved from the token, never a request parameter. */
    @Test
    void currentUserIsNotARequestParameter() {
        Map<String, Object> me = doc.read("$.paths['/api/auth/me'].get");
        assertThat(me).doesNotContainKey("parameters").doesNotContainKey("requestBody");
        List<String> parameterNames = doc.read("$.paths.*.*.parameters[*].name");
        assertThat(parameterNames).doesNotContain("currentUser", "principal", "authentication");
    }

    @Test
    void documentsPasswordsAsWriteOnlyAndNeverReturnsThem() {
        assertThat(doc.read("$.components.schemas.RegisterRequest.properties.password.writeOnly", Boolean.class))
                .isTrue();
        assertThat(doc.read("$.components.schemas.LoginRequest.properties.password.writeOnly", Boolean.class))
                .isTrue();
        Map<String, Object> authResponse = doc.read("$.components.schemas.AuthResponse.properties");
        assertThat(authResponse).containsOnlyKeys("accessToken", "tokenType", "expiresIn", "user");
        // Registration takes no role and no workspaceId: the first user is always a new workspace's ADMIN.
        Map<String, Object> registerFields = doc.read("$.components.schemas.RegisterRequest.properties");
        assertThat(registerFields).containsOnlyKeys("name", "email", "password", "workspaceName");
    }

    @Test
    void documentsTheErrorBody() {
        assertThat(doc.read("$.components.schemas.ApiErrorResponse.required", List.class))
                .containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path");
        assertThat(doc.read("$.components.responses.NotFound.content['application/json'].schema.$ref",
                String.class)).isEqualTo("#/components/schemas/ApiErrorResponse");
    }

    @Test
    void exposesNoEntitiesOrActuator() {
        Map<String, Object> schemas = doc.read("$.components.schemas");
        assertThat(schemas.keySet()).allMatch(name -> name.endsWith("Request") || name.endsWith("Response"));
        assertThat(json).doesNotContain("passwordHash", "/actuator");
    }

    /**
     * The accidental YAML converter (see WebMvcConfig) must stay removed. The
     * body is invalid on purpose: if YAML were read again the request would
     * fail validation (400) rather than register anyone.
     */
    @Test
    void apiIsJsonOnly() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/yaml")
                        .content("name: ''\n"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(json).doesNotContain("yaml", "*/*");
    }

    private List<String> enumValues(String schema, String property) {
        return doc.read("$.components.schemas." + schema + ".properties." + property + ".enum");
    }
}
