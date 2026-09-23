package com.queueflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps the REST API JSON-only.
 *
 * Spring Framework 7 auto-registers a YAML message converter whenever a
 * Jackson YAML module is on the classpath. QueueFlow has one only because
 * Springdoc's swagger-core depends on Jackson 2's jackson-dataformat-yaml,
 * which silently made every endpoint also accept and produce
 * application/yaml. That converter is removed here. Springdoc's own
 * /v3/api-docs.yaml export is unaffected: it serializes the document with
 * its own mapper, not through Spring MVC message converters.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(converters -> converters.removeIf(WebMvcConfig::isYamlConverter));
    }

    /**
     * Matches on the explicit YAML subtype. Not isCompatibleWith(): that would
     * also match converters declaring *&#47;* (byte array, String, ...).
     */
    private static boolean isYamlConverter(HttpMessageConverter<?> converter) {
        return converter.getSupportedMediaTypes().stream()
                .map(MediaType::getSubtype)
                .anyMatch(subtype -> subtype.equals("yaml") || subtype.endsWith("+yaml"));
    }
}
