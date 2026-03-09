package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Security permit paths")
class SecurityPermitPathsIntegrationTest extends BaseKeycloakIntegrationTest {

  @Test
  @DisplayName("actuator health is accessible without authentication")
  void actuatorHealth_shouldBePermitted() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/v2/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("actuator info is accessible without authentication")
  void actuatorInfo_shouldBePermitted() {
    ResponseEntity<String> response = restTemplate.getForEntity("/v2/actuator/info", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("protected endpoint returns 401 without authentication")
  void protectedEndpoint_shouldRequireAuthentication() {
    ResponseEntity<String> response = restTemplate.getForEntity("/v2/datasets", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
