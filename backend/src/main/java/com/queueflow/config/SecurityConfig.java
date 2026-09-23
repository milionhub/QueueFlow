package com.queueflow.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.queueflow.security.ApiSecurityErrorHandler;
import com.queueflow.security.CurrentUserJwtAuthenticationConverter;
import com.queueflow.user.UserRepository;

import jakarta.servlet.DispatcherType;

/**
 * The API is a stateless resource server: every /api/** request must carry
 * a valid bearer access token (issued by POST /api/auth/register or
 * /api/auth/login, the two public API operations), which Spring Security's
 * BearerTokenAuthenticationFilter verifies with the application's
 * JwtDecoder and CurrentUserJwtAuthenticationConverter resolves to the
 * current user (see AuthenticatedUser). Failures are answered with the
 * standard JSON error body by ApiSecurityErrorHandler.
 *
 * Deliberately not here yet: per-workspace access and ADMIN/MEMBER rules.
 * Those are decided by the services, which in this phase still also
 * receive the temporary client-supplied actorUserId / creatorId / authorId.
 */
@Configuration
public class SecurityConfig {

    /** The two ways to obtain a token: public, and never blocked by a stale token (see bearerTokenResolver). */
    private static final RequestMatcher TOKEN_ISSUING_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/auth/register"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/auth/login"));

    private final UserRepository userRepository;
    private final ApiSecurityErrorHandler securityErrorHandler;

    public SecurityConfig(UserRepository userRepository, ApiSecurityErrorHandler securityErrorHandler) {
        this.userRepository = userRepository;
        this.securityErrorHandler = securityErrorHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF protection defends credentials a browser attaches on
                // its own (cookies, HTTP auth). This API has none: the only
                // credential is a bearer token that the frontend's own code
                // puts in the Authorization header, which a cross-site form
                // or link cannot do. No session is created and no cookie
                // carries credentials, so CSRF tokens would protect nothing.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Spring Boot renders errors (validation failures,
                        // unhandled exceptions, 404s) via an internal ERROR
                        // dispatch to /error, and Spring Security authorizes
                        // that dispatch too. Without this, every error - even
                        // a 400 from a permitted request - is masked as a
                        // security error. Only the internal ERROR dispatch is
                        // permitted, not direct requests to /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // API documentation: read-only, describes the API and
                        // exposes no data. Springdoc serves the OpenAPI
                        // document at /v3/api-docs (plus .yaml and
                        // swagger-config) and the UI under /swagger-ui.
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers(TOKEN_ISSUING_ENDPOINTS).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Nothing else is served. Anonymous callers get 401,
                        // authenticated ones 403 - both as JSON.
                        .anyRequest().denyAll())
                // CORS preflight (OPTIONS) never reaches these rules: the
                // CorsFilter answers it first, without authentication.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new CurrentUserJwtAuthenticationConverter(userRepository)))
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler));
        return http.build();
    }

    /**
     * The standard header-only resolver (no access_token query or form
     * parameter), except that register and login ignore any token sent:
     * otherwise a client still holding an expired token would be refused
     * by the bearer filter when it tries to log in again.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerOnly = new DefaultBearerTokenResolver();
        return request -> TOKEN_ISSUING_ENDPOINTS.matches(request) ? null : headerOnly.resolve(request);
    }

    /**
     * The only browser origin allowed to call this backend: the Vite dev
     * server. Explicit origin, never a wildcard.
     */
    private static final List<String> ALLOWED_ORIGINS = List.of("http://localhost:5173");

    /**
     * The single CORS source for the application, applied by Spring
     * Security's CorsFilter (which also answers preflight OPTIONS requests
     * before authorization runs). Paths not registered here get no CORS
     * headers at all, so browsers block cross-origin calls to them.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        // REST API: the methods the API uses, the request headers the
        // frontend sends (Content-Type, and Authorization for the bearer
        // token), and Location exposed so the frontend can read it from 201
        // Created responses. allowCredentials stays false: it concerns
        // cookies and browser-managed HTTP auth, which this API does not use
        // - the Authorization header set by the frontend's own code does not
        // need it.
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins(ALLOWED_ORIGINS);
        api.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        api.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION));
        api.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        api.setAllowCredentials(false);

        // Actuator health: unchanged - read-only GET for the frontend's
        // connectivity check.
        CorsConfiguration health = new CorsConfiguration();
        health.setAllowedOrigins(ALLOWED_ORIGINS);
        health.setAllowedMethods(List.of(HttpMethod.GET.name()));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", api);
        source.registerCorsConfiguration("/actuator/health", health);
        return source;
    }
}
