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
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI document for the QueueFlow REST API (served by Springdoc at
 * /v3/api-docs, rendered at /swagger-ui). Documentation only: nothing here
 * changes runtime behavior.
 *
 * Security: one HTTP bearer (JWT) scheme, required by every operation
 * except those that explicitly opt out with an empty
 * {@code @SecurityRequirements} (register and login).
 */
@Configuration
public class OpenApiConfig {

    /** Reusable error responses, referenced from controllers as e.g. {@code ref = NOT_FOUND}. */
    public static final String BAD_REQUEST = "#/components/responses/BadRequest";
    public static final String UNAUTHORIZED = "#/components/responses/Unauthorized";
    public static final String FORBIDDEN = "#/components/responses/Forbidden";
    public static final String NOT_FOUND = "#/components/responses/NotFound";
    public static final String CONFLICT = "#/components/responses/Conflict";

    /** Shared wording for the temporary client-supplied identity parameter. */
    public static final String ACTOR_USER_ID = "Id of the user performing the operation. Temporary: still "
            + "supplied by the client in addition to the access token; it will be derived from the "
            + "authenticated user instead.";

    public static final String BEARER_AUTH = "bearerAuth";

    private static final String ERROR_SCHEMA = "ApiErrorResponse";

    @Bean
    OpenAPI queueFlowOpenApi() {
        Components components = new Components()
                .responses(Map.of(
                        "BadRequest", errorResponse(
                                "Invalid request: validation failure, malformed body or parameter, invalid "
                                        + "business value, or resources that cannot be related"),
                        "Unauthorized", errorResponse("Missing, invalid or expired access token - or, for "
                                + "login, credentials that do not identify a user"),
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

                                Authentication: register (which creates a workspace and its first, ADMIN, \
                                user) or log in to obtain an access token, then send it on every other request \
                                as "Authorization: Bearer <accessToken>". Tokens expire after one hour; there \
                                is no refresh token, so log in again. A missing, invalid or expired token is \
                                answered with 401.

                                Temporary: where an operation needs to know who is acting, the client still \
                                also supplies that user's id (actorUserId, creatorId, authorId). These inputs \
                                will be replaced by the authenticated user.

                                All requests and responses are JSON. Errors use the ApiErrorResponse body."""))
                .components(components.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/auth/register or /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * Applies the two rules that hold for the whole API, instead of repeating
     * annotations on every endpoint:
     * <ul>
     *   <li>Every operation with input can answer 400 (bean validation,
     *       malformed body, invalid path/query value, or a service-level
     *       business rule), and every operation that requires a token can
     *       answer 401.</li>
     *   <li>Every property of a response DTO and of ApiErrorResponse is always
     *       present in the JSON (null values are serialized, not omitted), so
     *       it is marked required; nullable ones say so via their own schema.</li>
     * </ul>
     */
    @Bean
    OpenApiCustomizer queueFlowOpenApiConventions() {
        return openApi -> {
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (hasInput(operation)) {
                    operation.getResponses().putIfAbsent("400", new ApiResponse().$ref(BAD_REQUEST));
                }
                if (requiresToken(operation)) {
                    operation.getResponses().putIfAbsent("401", new ApiResponse().$ref(UNAUTHORIZED));
                }
            }));

            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (name.endsWith("Response") && schema.getProperties() != null) {
                    schema.setRequired(List.copyOf(schema.getProperties().keySet()));
                }
            });
        };
    }

    /** Only an operation with parameters or a body can be rejected as a bad request. */
    private static boolean hasInput(Operation operation) {
        return operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    }

    /** Operations inherit the global bearer requirement unless they declare an empty list (public). */
    private static boolean requiresToken(Operation operation) {
        return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
    }
}
