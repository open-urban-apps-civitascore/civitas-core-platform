package de.civitascore.portal.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Core CIVITAS platform configuration properties bound from the {@code event.*} namespace. Contains
 * the config adapter timeout.
 */
@Validated
@ConfigurationProperties(prefix = "event")
public record EventProperties(@Positive int configAdapterTimeoutSeconds) {}
