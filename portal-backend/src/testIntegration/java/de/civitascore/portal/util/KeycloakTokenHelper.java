package de.civitascore.portal.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class KeycloakTokenHelper {

  private static final String DEFAULT_REALM = "iot";
  private static final String DEFAULT_CLIENT_ID = "test-client";
  private static final String DEFAULT_CLIENT_SECRET = "test-secret";

  private final KeycloakContainer keycloak;
  private final RestTemplate restTemplate;
  private final String realm;
  private final String clientId;
  private final String clientSecret;

  public KeycloakTokenHelper(KeycloakContainer keycloak) {
    this(keycloak, DEFAULT_REALM, DEFAULT_CLIENT_ID, DEFAULT_CLIENT_SECRET);
  }

  public KeycloakTokenHelper(
      KeycloakContainer keycloak, String realm, String clientId, String clientSecret) {
    this.keycloak = keycloak;
    this.restTemplate = new RestTemplate();
    this.realm = realm;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public String getAccessToken(String username, String password) {
    try {
      String tokenUrl = buildTokenUrl();
      HttpEntity<MultiValueMap<String, String>> request = createTokenRequest(username, password);

      KeycloakTokenResponse response =
          restTemplate.postForObject(tokenUrl, request, KeycloakTokenResponse.class);

      if (response == null || response.accessToken() == null) {
        throw new IllegalStateException("Failed to obtain access token");
      }

      return response.accessToken();
    } catch (RestClientException e) {
      throw new IllegalStateException("Failed to authenticate with Keycloak", e);
    }
  }

  private String buildTokenUrl() {
    return keycloak.getAuthServerUrl() + "/realms/" + realm + "/protocol/openid-connect/token";
  }

  private HttpEntity<MultiValueMap<String, String>> createTokenRequest(
      String username, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "password");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    formData.add("username", username);
    formData.add("password", password);

    return new HttpEntity<>(formData, headers);
  }

  public record KeycloakTokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Integer expiresIn) {}
}
