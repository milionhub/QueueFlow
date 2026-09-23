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
                "Workspaces", "Users", "Projects", "Tickets", "Labels", "Comments", "Activity");
    }

    @Test
    void documentsEveryApiOperationAndNothingElse() {
        Map<String, Object> paths = doc.read("$.paths");
        assertThat(paths.keySet()).allMatch(path -> path.startsWith("/api/"));
        List<String> operationIds = doc.read("$.paths.*.*.operationId");
        assertThat(operationIds).hasSize(27).doesNotHaveDuplicates();
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
    void documentsTheErrorBody() {
        assertThat(doc.read("$.components.schemas.ApiErrorResponse.required", List.class))
                .containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path");
        assertThat(doc.read("$.components.responses.NotFound.content['application/json'].schema.$ref",
                String.class)).isEqualTo("#/components/schemas/ApiErrorResponse");
    }

    @Test
    void exposesNoEntitiesActuatorOrSecurityScheme() {
        Map<String, Object> schemas = doc.read("$.components.schemas");
        assertThat(schemas.keySet()).allMatch(name -> name.endsWith("Request") || name.endsWith("Response"));
        assertThat(json).doesNotContain("passwordHash", "/actuator");
        assertThat(json).doesNotContain("securitySchemes", "\"security\"");
    }

    /**
     * The accidental YAML converter (see WebMvcConfig) must stay removed. The
     * body is invalid on purpose: if YAML were read again the request would
     * fail validation (400) rather than create a workspace.
     */
    @Test
    void apiIsJsonOnly() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/yaml")
                        .content("name: ''\n"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(json).doesNotContain("yaml", "*/*");
    }

    private List<String> enumValues(String schema, String property) {
        return doc.read("$.components.schemas." + schema + ".properties." + property + ".enum");
    }
}
