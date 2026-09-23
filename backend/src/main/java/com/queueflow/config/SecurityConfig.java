package com.queueflow.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

/**
 * TEMPORARY (Phase 1 REST API development only): no authentication
 * mechanism exists yet, so this opens unauthenticated access to the
 * Actuator health endpoint and to the /api/** REST endpoints so they can be
 * exercised during development. Every other endpoint keeps Spring
 * Security's "authenticated by default" behavior (and, with no login
 * mechanism configured, is therefore unreachable).
 *
 * Phase 2 replaces this with real JWT-based authentication and
 * ADMIN/MEMBER authorization - the /api/** permitAll() below must not
 * survive into that phase.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF protection defends cookie/session-authenticated
                // browser requests. This is a stateless JSON API: no
                // session is ever created and no cookie carries
                // credentials (Phase 2 will use a bearer token in the
                // Authorization header), so there is no ambient credential
                // for a cross-site request to ride on. Disabled
                // intentionally so POST/PATCH/DELETE on /api/** work
                // without a CSRF token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Spring Boot renders errors (validation failures,
                        // unhandled exceptions, 404s) via an internal ERROR
                        // dispatch to /error, and Spring Security
                        // authorizes that dispatch too. Without this, every
                        // error - even a 400 from a permitted /api/**
                        // request - is masked as an empty 403. Only the
                        // internal ERROR dispatch is permitted, not direct
                        // requests to /error. Not temporary: keep in Phase 2.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // API documentation: read-only, and describes only
                        // what the (currently open) /api/** endpoints already
                        // expose. Springdoc serves the OpenAPI document at
                        // /v3/api-docs (plus .yaml and swagger-config) and the
                        // UI under /swagger-ui.
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // TEMPORARY - see class Javadoc.
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
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
        // REST API (development): the methods the API uses, the request
        // headers the frontend sends (Authorization already allowed for the
        // Phase 2 bearer token), and Location exposed so the frontend can
        // read it from 201 Created responses. No credentials/cookies: auth
        // will be a bearer token, not a cookie.
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
