package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.output.MappingOutputDTO;
import de.civitascore.portal.model.output.MappingSummaryDTO;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class MappingControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String NOT_FOUND = "urn:civitas:error:NOT_FOUND";

  @Autowired private PortalTestDataFactory portalData;

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  @Test
  void createMapping_answers201WithLocationOfTheNewItem() {
    DataSet dataSet = portalData.dataSetWithManifest();

    ResponseEntity<MappingOutputDTO> created = create(dataSet, "Sensors");

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    MappingOutputDTO body = created.getBody();
    assertThat(body).isNotNull();
    assertThat(created.getHeaders().getLocation())
        .asString()
        .endsWith("/datasets/" + dataSet.getId() + "/mappings/" + body.mappingId());
    assertThat(body.logicalUrn()).endsWith(":" + body.mappingId());
    assertThat(body.title()).isEqualTo("Sensors");
  }

  @Test
  void updateMapping_storesANewVersionThatGetMappingReturns() {
    DataSet dataSet = portalData.dataSetWithManifest();
    MappingOutputDTO created = create(dataSet, "Sensors").getBody();
    Map<String, Object> fields = Map.of("$.id", Map.of("op", "copy", "sourcePath", "$.id"));

    ResponseEntity<MappingOutputDTO> updated =
        exchange(
            HttpMethod.PUT,
            item(dataSet.getId(), created.mappingId()),
            Map.of("title", "Sensors", "fields", fields),
            MappingOutputDTO.class);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    MappingOutputDTO version = updated.getBody();
    assertThat(version.mappingId()).isEqualTo(created.mappingId());
    assertThat(version.logicalUrn()).isEqualTo(created.logicalUrn());
    assertThat(version.version()).isNotEqualTo(created.version());

    MappingOutputDTO read = get(dataSet.getId(), created.mappingId()).getBody();
    assertThat(read.version()).isEqualTo(version.version());
    assertThat(read.versionedUrn()).isEqualTo(version.versionedUrn());
    assertThat(read.fields()).isEqualTo(fields);
  }

  @Test
  void listMappings_returnsOneEntryPerMappingOfTheDataSet() {
    DataSet dataSet = portalData.dataSetWithManifest();
    MappingOutputDTO first = create(dataSet, "First").getBody();
    MappingOutputDTO second = create(dataSet, "Second").getBody();
    create(portalData.dataSetWithManifest(), "Elsewhere");

    ResponseEntity<List<MappingSummaryDTO>> listed =
        restTemplate.exchange(
            collection(dataSet.getId()),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            new ParameterizedTypeReference<>() {});

    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listed.getBody())
        .containsExactlyInAnyOrder(
            new MappingSummaryDTO(first.mappingId(), first.logicalUrn(), "First"),
            new MappingSummaryDTO(second.mappingId(), second.logicalUrn(), "Second"));
  }

  @Test
  void deleteMapping_answers204AndTheMappingIsGone() {
    DataSet dataSet = portalData.dataSetWithManifest();
    MappingOutputDTO created = create(dataSet, "Sensors").getBody();

    ResponseEntity<Void> deleted =
        exchange(HttpMethod.DELETE, item(dataSet.getId(), created.mappingId()), null, Void.class);

    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    ResponseEntity<ProblemDetail> gone =
        exchange(
            HttpMethod.GET, item(dataSet.getId(), created.mappingId()), null, ProblemDetail.class);
    assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(gone.getBody().getType()).hasToString(NOT_FOUND);
  }

  // Guards the isolation that OPA cannot give, because OPA checks only the Dataset scope.
  @ParameterizedTest
  @ValueSource(strings = {"GET", "PUT", "DELETE"})
  void itemRoutes_whenTheMappingBelongsToAnotherDataSet_answer404NotFound(String method) {
    DataSet owner = portalData.dataSetWithManifest();
    DataSet other = portalData.dataSetWithManifest();
    MappingOutputDTO created = create(owner, "Sensors").getBody();

    ResponseEntity<ProblemDetail> response =
        exchange(
            HttpMethod.valueOf(method),
            item(other.getId(), created.mappingId()),
            body("Changed"),
            ProblemDetail.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getType()).hasToString(NOT_FOUND);
    MappingOutputDTO unchanged = get(owner.getId(), created.mappingId()).getBody();
    assertThat(unchanged.version()).isEqualTo(created.version());
  }

  @Test
  void itemRoutes_whenTheMappingIdIsUnknown_answerLikeAForeignMapping() {
    DataSet owner = portalData.dataSetWithManifest();
    DataSet other = portalData.dataSetWithManifest();
    MappingOutputDTO foreign = create(owner, "Sensors").getBody();

    ProblemDetail foreignAnswer = get(other.getId(), foreign.mappingId(), ProblemDetail.class);
    ProblemDetail unknownAnswer = get(other.getId(), UUID.randomUUID(), ProblemDetail.class);

    assertThat(unknownAnswer.getStatus()).isEqualTo(foreignAnswer.getStatus());
    assertThat(unknownAnswer.getType()).isEqualTo(foreignAnswer.getType());
    assertThat(unknownAnswer.getTitle()).isEqualTo(foreignAnswer.getTitle());
  }

  private ResponseEntity<MappingOutputDTO> create(DataSet dataSet, String title) {
    ResponseEntity<MappingOutputDTO> created =
        exchange(HttpMethod.POST, collection(dataSet.getId()), body(title), MappingOutputDTO.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return created;
  }

  private ResponseEntity<MappingOutputDTO> get(UUID dataSetId, UUID mappingId) {
    return exchange(HttpMethod.GET, item(dataSetId, mappingId), null, MappingOutputDTO.class);
  }

  private <T> T get(UUID dataSetId, UUID mappingId, Class<T> type) {
    return exchange(HttpMethod.GET, item(dataSetId, mappingId), null, type).getBody();
  }

  private static Map<String, Object> body(String title) {
    return Map.of("title", title, "fields", Map.of());
  }

  private <T> ResponseEntity<T> exchange(
      HttpMethod method, String path, Object body, Class<T> type) {
    Object sent = HttpMethod.PUT.equals(method) || HttpMethod.POST.equals(method) ? body : null;
    return restTemplate.exchange(path, method, new HttpEntity<>(sent, headers()), type);
  }

  private static String collection(UUID dataSetId) {
    return "/datasets/" + dataSetId + "/mappings";
  }

  private static String item(UUID dataSetId, UUID mappingId) {
    return collection(dataSetId) + "/" + mappingId;
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    return headers;
  }
}
