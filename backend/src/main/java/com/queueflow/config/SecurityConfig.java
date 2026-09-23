package com.queueflow.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        // TEMPORARY - see class Javadoc.
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name()));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/actuator/health", configuration);
        return source;
    }
}
