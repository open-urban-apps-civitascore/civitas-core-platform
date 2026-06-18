package de.civitascore.portal.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Core CIVITAS platform configuration properties bound from the {@code keycloak.*} namespace.
 * Contains the target Keycloak realm to connect to and the URL of the Keycloak auth server.
 */
@Validated
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
    @NotBlank String targetRealm, @NotBlank String authServerUrl, @NotBlank String realm) {}
