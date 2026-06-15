package de.civitascore.portal.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Core CIVITAS platform configuration properties bound from the {@code saga.*} namespace. Contains
 * the trigger topic name and the publish timeout in seconds.
 */
@Validated
@ConfigurationProperties(prefix = "saga")
public record SagaProperties(
    @NotBlank @DefaultValue("de.civitascore.dataset.saga.trigger") String triggerTopic,
    @Positive @DefaultValue("10") int publishTimeoutSeconds) {}
