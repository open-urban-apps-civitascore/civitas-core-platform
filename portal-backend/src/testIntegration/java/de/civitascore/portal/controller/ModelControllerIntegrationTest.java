package de.civitascore.portal.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

@DisplayName("Model Controller Integration Tests")
@EnableWireMock(
    @ConfigureWireMock(
        baseUrlProperties = {"model-atlas.base-url"},
        portProperties = "model-atlas.port"))
class ModelControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String MODELS_ENDPOINT = "/models";
  private static final String UPLOAD_ENDPOINT = MODELS_ENDPOINT + "/upload";
  private static final String DOWNLOAD_ENDPOINT = MODELS_ENDPOINT + "/download";

  private static final String TEST_NS_URI = "http://test/test/myuml/1.0.0";
  private static final String TEST_MODEL_FILE_PATH = "mocks/models/Simple_model.xmi";
  private static final String TEST_SCOPE = "default";
  private static final String TEST_STAGE = "draft";

  private static final String MOCK_MODEL_RESPONSE_JSON =
      """
          {
              "eClass": "http://www.eclipse.org/emf/2002/Ecore#//EPackage",
              "name": "myTestModel",
              "nsURI": "http://test/test/myuml/1.0.42",
              "nsPrefix": "myTestModel",
              "eSubpackages": [
                  {
                      "name": "thePackage",
                      "nsURI": "http://test/test/myuml",
                      "nsPrefix": "myTestModel.thePackage",
                      "eClassifiers": [
                          {
                              "eClass": "http://www.eclipse.org/emf/2002/Ecore#//EClass",
                              "name": "Baum",
                              "eStructuralFeatures": [
                                  {
                                      "eClass": "http://www.eclipse.org/emf/2002/Ecore#//EReference",
                                      "name": "forest",
                                      "ordered": false,
                                      "lowerBound": 1,
                                      "eType": {
                                          "eClass": "http://www.eclipse.org/emf/2002/Ecore#//EClass",
                                          "$ref": "http://apicurio-registry:8080/apis/registry/v3/groups/default-draft/artifacts/aHR0cDovL3Rlc3QvdGVzdC9teXVtbC8xLjAuNDI=/versions/1.0.42/aHR0cDovL3Rlc3QvdGVzdC9teXVtbC8xLjAuNDI=#//thePackage/Wald"
                                      }
                                  }
                              ]
                          },
                          {
                              "eClass": "http://www.eclipse.org/emf/2002/Ecore#//EClass",
                              "name": "Wald",
                              "eStructuralFeatures": [
                                  {
                                      "eClass": "http://www.eclipse.org/emf/2002/Ecore#//EReference",
                                      "name": "trees",
                                      "ordered": false,
                                      "lowerBound": 1,
                                      "upperBound": -1,
                                      "eType": {
                                          "eClass": "http://www.eclipse.org/emf/2002/Ecore#//EClass",
                                          "$ref": "http://apicurio-registry:8080/apis/registry/v3/groups/default-draft/artifacts/aHR0cDovL3Rlc3QvdGVzdC9teXVtbC8xLjAuNDI=/versions/1.0.42/aHR0cDovL3Rlc3QvdGVzdC9teXVtbC8xLjAuNDI=#//thePackage/Baum"
                                      }
                                  }
                              ]
                          }
                      ]
                  }
              ]
          }
          """;

  @Test
  @DisplayName("Should upload model file successfully with valid request")
  void shouldUploadModelSuccessfully() throws IOException {
    String expectedPath =
        String.format(
            "/atlas/rest/%s/schema/stages/%s?nsUri=%s&overwrite=true",
            TEST_SCOPE, TEST_STAGE, TEST_NS_URI);

    ClassPathResource modelFile = new ClassPathResource(TEST_MODEL_FILE_PATH);
    byte[] fileContent = modelFile.getContentAsByteArray();

    stubFor(
        post(urlEqualTo(expectedPath))
            .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/uml"))
            .withRequestBody(binaryEqualTo(fileContent))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/uml")
                    .withBody(MOCK_MODEL_RESPONSE_JSON)));

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("modelFile", modelFile);
    body.add("nsUri", TEST_NS_URI);

    HttpHeaders headers = createAuthHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            UPLOAD_ENDPOINT,
            new org.springframework.http.HttpEntity<>(body, headers),
            String.class);

    assertThat(response.getStatusCode())
        .as("Should return CREATED status")
        .isEqualTo(HttpStatus.CREATED);

    assertThat(response.getBody())
        .as("Response body should match the mocked response")
        .isEqualTo(MOCK_MODEL_RESPONSE_JSON);

    verify(
        postRequestedFor(urlEqualTo(expectedPath))
            .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/uml"))
            .withRequestBody(binaryEqualTo(fileContent)));
  }

  @Test
  @DisplayName("Should download model file successfully with correct accept header")
  void shouldDownloadModelSuccessfully() {
    String expectedPath =
        String.format(
            "/atlas/rest/%s/schema/stages/%s/content?nsUri=%s",
            TEST_SCOPE, TEST_STAGE, TEST_NS_URI);

    String acceptHeader = MediaType.APPLICATION_XML_VALUE;

    stubFor(
        get(expectedPath)
            .withHeader(HttpHeaders.ACCEPT, equalTo(acceptHeader))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, acceptHeader)
                    .withBody(MOCK_MODEL_RESPONSE_JSON)));

    HttpHeaders headers = createAuthHeaders();
    headers.setAccept(MediaType.parseMediaTypes(acceptHeader));

    String urlWithParams = DOWNLOAD_ENDPOINT + "?nsUri=" + TEST_NS_URI;

    ResponseEntity<String> response =
        restTemplate.exchange(
            urlWithParams,
            HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

    assertThat(response.getBody())
        .as("Response body should match the mocked response")
        .isEqualTo(MOCK_MODEL_RESPONSE_JSON);

    assertThat(response.getHeaders().getContentType())
        .as("Response content type should match the accept header")
        .isEqualTo(MediaType.APPLICATION_XML);

    verify(
        getRequestedFor(urlEqualTo(expectedPath))
            .withHeader(HttpHeaders.ACCEPT, equalTo(acceptHeader)));
  }

  private HttpHeaders createAuthHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    return headers;
  }
}
