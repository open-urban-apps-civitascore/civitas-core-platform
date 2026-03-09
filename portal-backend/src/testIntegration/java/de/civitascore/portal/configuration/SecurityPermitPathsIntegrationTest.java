package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Security permit paths")
class SecurityPermitPathsIntegrationTest extends BaseKeycloakIntegrationTest {

  @Value("${server.servlet.context-path}")
  private String contextPath;

  @Autowired private SecurityProperties securityProperties;

  @Test
  @DisplayName("security properties are loaded from YAML configuration")
  void securityProperties_shouldBeLoaded() {
    assertThat(securityProperties.permitPaths()).isNotEmpty();
    assertThat(securityProperties.permitPaths()).contains("/actuator/health/**", "/actuator/info");
  }

  @Test
  @DisplayName("protected endpoint returns 401 without authentication")
  void protectedEndpoint_shouldRequireAuthentication() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(contextPath + "/datasets", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("unknown endpoint returns 401 without authentication, not 404")
  void unknownEndpoint_shouldRequireAuthenticationBeforeReturning404() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(contextPath + "/nonexistent", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
