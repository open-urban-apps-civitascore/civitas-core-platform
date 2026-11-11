package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenApiConfigTest {

  @Test
  @DisplayName("shows the openapi.oauth-server-url in the OAuth2 flow when it is set")
  void oauthServerUrlOverridesKeycloakUrl() {
    OpenApiConfig config =
        new OpenApiConfig(
            new KeycloakProperties(
                "civitas-core", "http://keycloak:8080", "civitas-core", true, false),
            new OpenApiProperties("https://auth.example.org"));

    OAuthFlow flow = authorizationCodeFlow(config.customOpenAPI());

    assertThat(flow.getAuthorizationUrl())
        .isEqualTo("https://auth.example.org/realms/civitas-core/protocol/openid-connect/auth");
    assertThat(flow.getTokenUrl())
        .isEqualTo("https://auth.example.org/realms/civitas-core/protocol/openid-connect/token");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  @DisplayName("shows the keycloak.auth-server-url in the OAuth2 flow when no override is set")
  void fallsBackToKeycloakUrl(String oauthServerUrl) {
    OpenApiConfig config =
        new OpenApiConfig(
            new KeycloakProperties(
                "civitas-core", "http://keycloak:8080", "civitas-core", true, false),
            new OpenApiProperties(oauthServerUrl));

    OAuthFlow flow = authorizationCodeFlow(config.customOpenAPI());

    assertThat(flow.getAuthorizationUrl())
        .isEqualTo("http://keycloak:8080/realms/civitas-core/protocol/openid-connect/auth");
    assertThat(flow.getTokenUrl())
        .isEqualTo("http://keycloak:8080/realms/civitas-core/protocol/openid-connect/token");
  }

  private static OAuthFlow authorizationCodeFlow(OpenAPI openApi) {
    return openApi
        .getComponents()
        .getSecuritySchemes()
        .get("oauth2")
        .getFlows()
        .getAuthorizationCode();
  }
}
