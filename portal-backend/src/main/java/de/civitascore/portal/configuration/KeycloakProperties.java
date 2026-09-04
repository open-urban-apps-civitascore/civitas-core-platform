package de.civitascore.portal.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Core CIVITAS platform configuration properties bound from the {@code keycloak.*} namespace.
 * Contains the target Keycloak realm to connect to and the URL of the Keycloak auth server.
 *
 * @param enforceOtp whether newly created users must configure TOTP (the {@code CONFIGURE_TOTP}
 *     required action). Bound from {@code KEYCLOAK_ENFORCE_OTP} and defaults to {@code true}.
 */
@Validated
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
    @NotBlank String targetRealm,
    @NotBlank String authServerUrl,
    @NotBlank String realm,
    @DefaultValue("true") boolean enforceOtp) {}
