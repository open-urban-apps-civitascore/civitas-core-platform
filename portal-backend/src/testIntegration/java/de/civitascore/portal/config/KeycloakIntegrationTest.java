package de.civitascore.portal.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Keycloak Integration Tests")
class KeycloakIntegrationTest extends BaseKeycloakIntegrationTest {

  @Nested
  @DisplayName("Container Startup")
  class ContainerStartupTests {

    @Test
    @DisplayName("Should start all required containers successfully")
    void shouldStartContainers() {
      assertThat(POSTGRES.isRunning()).as("PostgreSQL container should be running").isTrue();
      assertThat(KEYCLOAK.isRunning()).as("Keycloak container should be running").isTrue();

      String authServerUrl = KEYCLOAK.getAuthServerUrl();
      assertThat(authServerUrl).as("Auth server URL should be available").isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Authentication Tests")
  class AuthenticationTests {

    @Test
    @DisplayName("Should return 401 when no token provided")
    void shouldReturn401WithoutToken() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              "/users/me", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should return user details when valid token provided")
    void shouldReturnUserDetailsWithValidToken() {
      String accessToken = getValidAccessToken();
      HttpHeaders headers = createAuthHeaders(accessToken);

      ResponseEntity<PrincipalUserDetails> response =
          restTemplate.exchange(
              "/users/me", HttpMethod.GET, new HttpEntity<>(headers), PrincipalUserDetails.class);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();
      assertThat(response.getBody().getUsername())
          .as("Username should match expected value")
          .isEqualTo("testuser");
    }
  }

  private HttpHeaders createAuthHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }
}
