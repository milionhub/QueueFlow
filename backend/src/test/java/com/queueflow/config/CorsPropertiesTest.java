package com.queueflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Only exact http(s) origins are accepted: anything looser stops the application at startup. */
class CorsPropertiesTest {

    @Test
    void acceptsOneOrMoreExactOriginsAndTrimsThem() {
        CorsProperties properties = new CorsProperties(
                List.of("http://localhost:5173", " https://queueflow.example ", "http://192.168.1.38:5173"));

        assertThat(properties.allowedOrigins())
                .containsExactly("http://localhost:5173", "https://queueflow.example", "http://192.168.1.38:5173");
    }

    @Test
    void rejectsAMissingOrEmptyList() {
        assertThatThrownBy(() -> new CorsProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one origin");
        assertThatThrownBy(() -> new CorsProperties(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one origin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "http://*.example.com", "https://*", "localhost:5173", "ftp://example.com",
            "http://localhost:5173/", "http://localhost:5173/app", "http://localhost:5173?x=1",
            "http://user@localhost:5173", "", "not a url"})
    void rejectsAnythingThatIsNotAnExactOrigin(String origin) {
        assertThatThrownBy(() -> new CorsProperties(List.of("http://localhost:5173", origin)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact origins");
    }
}
