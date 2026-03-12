package de.civitascore.portal.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.civitascore.portal.configuration.ModelAtlasConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

@DisplayName("ModelRestClientRequestService")
class ModelRestClientRequestServiceTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private ModelRestClientRequestService service;

  @BeforeEach
  void setUp() {
    ModelAtlasConfig config = new ModelAtlasConfig();
    config.setBaseUrl(wireMock.baseUrl());
    config.setScope("testScope");
    config.setStage("testStage");
    service = new ModelRestClientRequestService(config, RestClient.builder());
  }

  @Test
  @DisplayName("uploadModelString should transmit German umlauts without corruption")
  void uploadModelStringShouldPreserveGermanUmlauts() {
    String xmlWithUmlauts = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><model>äöüÄÖÜß</model>";

    wireMock.stubFor(
        post(urlPathMatching("/atlas/rest/testScope/schema/stages/testStage"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

    service.uploadModelString(xmlWithUmlauts, "http://example.com/model");

    List<LoggedRequest> requests =
        wireMock.findAll(
            postRequestedFor(urlPathMatching("/atlas/rest/testScope/schema/stages/testStage")));
    assertThat(requests).hasSize(1);
    String body = new String(requests.get(0).getBody(), StandardCharsets.UTF_8);
    assertThat(body).contains("äöüÄÖÜß");
  }
}
