package com.queueflow.config;

import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.queueflow.common.web.ApiErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * OpenAPI document for the QueueFlow REST API (served by Springdoc at
 * /v3/api-docs, rendered at /swagger-ui). Documentation only: nothing here
 * changes runtime behavior.
 *
 * Deliberately no security scheme: Phase 1 endpoints are unauthenticated,
 * and bearer authentication is documented when Phase 2 implements it.
 */
@Configuration
public class OpenApiConfig {

    /** Reusable error responses, referenced from controllers as e.g. {@code ref = NOT_FOUND}. */
    public static final String BAD_REQUEST = "#/components/responses/BadRequest";
    public static final String FORBIDDEN = "#/components/responses/Forbidden";
    public static final String NOT_FOUND = "#/components/responses/NotFound";
    public static final String CONFLICT = "#/components/responses/Conflict";

    /** Shared wording for the temporary client-supplied identity parameter. */
    public static final String ACTOR_USER_ID = "Id of the user performing the operation. Temporary Phase 1 "
            + "identity input supplied by the client; Phase 2 authentication will derive the actor from the "
            + "authenticated user instead.";

    private static final String ERROR_SCHEMA = "ApiErrorResponse";

    @Bean
    OpenAPI queueFlowOpenApi() {
        Components components = new Components()
                .responses(Map.of(
                        "BadRequest", errorResponse(
                                "Invalid request: validation failure, malformed body or parameter, invalid "
                                        + "business value, or resources that cannot be related"),
                        "Forbidden", errorResponse("The acting user is not allowed to perform this operation"),
                        "NotFound", errorResponse("A referenced resource does not exist"),
                        "Conflict", errorResponse("The request conflicts with existing data")));
        ModelConverters.getInstance(true).read(ApiErrorResponse.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("QueueFlow API")
                        .version("v1")
                        .description("""
                                QueueFlow is a lightweight issue and project tracking API for small software \
                                teams: workspaces and their members, projects, tickets with per-project numbers \
                                (e.g. CORE-7), labels, comments and an automatic per-ticket activity history.

                                This is the Phase 1 core API. It is not yet authenticated: where an operation \
                                needs to know who is acting, the client currently supplies that user's id \
                                (actorUserId, creatorId, authorId). Authentication is introduced in Phase 2 \
                                and will replace client-supplied identity where appropriate.

                                All requests and responses are JSON. Errors use the ApiErrorResponse body."""))
                .components(components);
    }

    /**
     * Applies the two rules that hold for the whole API, instead of repeating
     * annotations on every endpoint:
     * <ul>
     *   <li>Every operation can answer 400 (bean validation, malformed body,
     *       invalid path/query value, or a service-level business rule).</li>
     *   <li>Every property of a response DTO and of ApiErrorResponse is always
     *       present in the JSON (null values are serialized, not omitted), so
     *       it is marked required; nullable ones say so via their own schema.</li>
     * </ul>
     */
    @Bean
    OpenApiCustomizer queueFlowOpenApiConventions() {
        return openApi -> {
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().putIfAbsent("400", new ApiResponse().$ref(BAD_REQUEST))));

            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (name.endsWith("Response") && schema.getProperties() != null) {
                    schema.setRequired(List.copyOf(schema.getProperties().keySet()));
                }
            });
        };
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
    }
}
