package com.queueflow.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The browser origins allowed to call this backend across origins, bound
 * from {@code queueflow.cors.allowed-origins} (see application.yml; set with
 * QUEUEFLOW_CORS_ALLOWED_ORIGINS, comma-separated). The default is the Vite
 * dev server, http://localhost:5173.
 *
 * Every entry must be an exact origin - scheme, host and optional port, no
 * path - so a typo or a wildcard stops the application at startup instead
 * of quietly allowing too much or too little. A frontend served from the
 * backend's own origin (behind the Docker stack's nginx) needs no entry:
 * same-origin requests are not CORS requests.
 *
 * @param allowedOrigins the exact origins, e.g. {@code http://localhost:5173}
 */
@ConfigurationProperties("queueflow.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("queueflow.cors.allowed-origins must list at least one origin");
        }
        allowedOrigins = allowedOrigins.stream().map(String::strip).toList();
        for (String origin : allowedOrigins) {
            requireExactOrigin(origin);
        }
    }

    private static void requireExactOrigin(String origin) {
        String problem = "queueflow.cors.allowed-origins must contain exact origins such as "
                + "http://localhost:5173 (scheme and host, optional port, no path or wildcard), not: " + origin;
        if (origin.contains("*")) {
            throw new IllegalArgumentException(problem);
        }
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException(problem, invalid);
        }
        boolean httpScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        boolean originOnly = uri.getRawPath() == null || uri.getRawPath().isEmpty();
        if (!httpScheme || uri.getHost() == null || !originOnly || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(problem);
        }
    }
}
