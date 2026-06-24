package de.civitascore.portal.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Integration tests for the security filter chain configured in {@code SecurityConfig}. */
class SecurityConfigIntegrationTest extends BaseKeycloakIntegrationTest {

  private ResponseEntity<String> getDataSources() {
    HttpHeaders authHeaders = new HttpHeaders();
    authHeaders.setBearerAuth(getValidAccessToken());
    authHeaders.set(AllowedScopesFilter.HEADER_NAME, "*");
    return restTemplate.exchange(
        "/v1/datasources", HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);
  }

  @Test
  @DisplayName("Should send Cache-Control no-store on authenticated API responses")
  void shouldSendNoStoreCacheControlOnAuthenticatedResponse() {
    // Guards against a change that disables Spring Security's default security headers.
    String cacheControl = getDataSources().getHeaders().getCacheControl();
    assertThat(cacheControl).isNotNull().contains("no-store").contains("no-cache");
  }

  @Test
  @DisplayName("Should send hardening headers on authenticated API responses")
  void shouldSendHardeningHeadersOnAuthenticatedResponse() {
    HttpHeaders responseHeaders = getDataSources().getHeaders();
    assertThat(responseHeaders.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(responseHeaders.getFirst("X-Frame-Options")).isEqualTo("DENY");
  }
}
