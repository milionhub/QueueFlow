package com.queueflow.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.queueflow.common.web.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the standard {@link ApiErrorResponse} for failures that Spring
 * Security decides before any controller runs, where GlobalExceptionHandler
 * cannot help:
 * <ul>
 *   <li>401 (AuthenticationEntryPoint): no bearer token on a protected route
 *       ("Authentication required"), or a token that is malformed, badly
 *       signed, expired, from another issuer or for a user who no longer
 *       exists ("Invalid or expired token" - never which).</li>
 *   <li>403 (AccessDeniedHandler): an authenticated caller refused by the
 *       security rules themselves. Business-level 403s (e.g. editing someone
 *       else's comment) stay in the services and GlobalExceptionHandler.</li>
 * </ul>
 * WWW-Authenticate follows RFC 6750, but is written here instead of by
 * Spring's BearerTokenAuthenticationEntryPoint because that one copies the
 * token parser's own error description into the header.
 */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String AUTHENTICATION_REQUIRED = "Authentication required";
    static final String INVALID_TOKEN = "Invalid or expired token";
    static final String ACCESS_DENIED = "Access denied";

    private final JsonMapper jsonMapper;

    public ApiSecurityErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * A bearer token that was present but rejected reaches this method as an
     * OAuth2AuthenticationException (from the bearer-token filter); a request
     * without any token arrives here from the authorization check instead.
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        boolean tokenRejected = exception instanceof OAuth2AuthenticationException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, tokenRejected ? "Bearer error=\"invalid_token\"" : "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, tokenRejected ? INVALID_TOKEN : AUTHENTICATION_REQUIRED, request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(response, HttpStatus.FORBIDDEN, ACCESS_DENIED, request);
    }

    private void write(HttpServletResponse response, HttpStatus status, String message, HttpServletRequest request)
            throws IOException {
        // getRequestURI(): path only, as in GlobalExceptionHandler.
        ApiErrorResponse body = ApiErrorResponse.of(status, message, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
