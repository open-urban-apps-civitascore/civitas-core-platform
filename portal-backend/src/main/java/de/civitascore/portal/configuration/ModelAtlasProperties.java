package de.civitascore.portal.configuration;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Model Atlas service integration, bound from the {@code
 * model-atlas.*} namespace. Defines the base URL, scope, stage, and HTTP timeout settings.
 */
@Validated
@ConfigurationProperties(prefix = "model-atlas")
public record ModelAtlasProperties(
    @NotBlank @URL(message = "model-atlas.baseUrl must be a valid URL") String baseUrl,
    @NotBlank String scope,
    @NotBlank String stage,
    @DefaultValue("5000") int connectTimeoutMs,
    @DefaultValue("30000") int readTimeoutMs) {}
