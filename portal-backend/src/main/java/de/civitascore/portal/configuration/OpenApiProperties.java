package de.civitascore.portal.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAPI description configuration bound from the {@code openapi.*} namespace.
 *
 * @param oauthServerUrl the Keycloak base URL that the OAuth2 security scheme of the OpenAPI
 *     description shows. When blank, the description uses {@code keycloak.auth-server-url}. Set it
 *     when the description is published outside the deployment, so that the URLs in it do not point
 *     at an internal host — e.g. {@code https://auth.example.org} for the committed spec.
 */
@ConfigurationProperties(prefix = "openapi")
public record OpenApiProperties(String oauthServerUrl) {}
