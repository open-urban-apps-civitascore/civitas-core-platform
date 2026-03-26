package de.civitascore.portal.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Core CIVITAS platform configuration properties bound from the {@code civitas.*} namespace.
 * Contains the master encryption key used for credential encryption.
 */
@Validated
@ConfigurationProperties(prefix = "civitas")
public record CivitasProperties(@NotBlank String masterKey) {}
