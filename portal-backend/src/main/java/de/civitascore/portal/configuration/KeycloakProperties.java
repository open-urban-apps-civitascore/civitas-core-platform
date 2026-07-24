package de.civitascore.portal.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Core CIVITAS platform configuration properties bound from the {@code keycloak.*} namespace.
 * Contains the target Keycloak realm to connect to, the URL of the Keycloak auth server, and the
 * one-shot group-member backfill switch.
 *
 * @param targetRealm the Keycloak realm that groups, users and roles are synced into
 * @param authServerUrl the base URL of the Keycloak auth server
 * @param realm the Keycloak realm used for authenticating incoming requests
 * @param enforceOtp whether newly created users must configure TOTP (the {@code CONFIGURE_TOTP}
 *     required action). Bound from {@code KEYCLOAK_ENFORCE_OTP} and defaults to {@code true}.
 * @param groupMemberBackfill one-shot switch: when {@code true}, startup re-emits GROUP_UPDATED for
 *     every already-synced group so their members are reconciled into Keycloak. Default {@code
 *     false}. Turn it on for a single rollout deploy, then off again — the reconcile is idempotent,
 *     but leaving it on makes every boot re-publish for all synced groups.
 */
@Validated
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
    @NotBlank String targetRealm,
    @NotBlank String authServerUrl,
    @NotBlank String realm,
    @DefaultValue("true") boolean enforceOtp,
    @DefaultValue("false") boolean groupMemberBackfill) {}
