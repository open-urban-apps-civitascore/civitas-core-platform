package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.security.AllowedScopesFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class PublishedStructureControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  @Test
  void getPublishedStructure_unknownKey_answers404NotFoundProblem() {
    String unknownKey = "no-such-structure";
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");

    ResponseEntity<ProblemDetail> response =
        restTemplate.exchange(
            "/published-structures/" + unknownKey,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ProblemDetail.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getType()).hasToString("urn:civitas:error:NOT_FOUND");
    assertThat(response.getBody().getDetail()).doesNotContain(unknownKey);
  }
}
