package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

@DisplayName("Removing the last OWS NamedApi cleans up the dataset's layers")
class DataSetNamedApiLayerCleanupIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private LayerRepository layerRepository;

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /** A DRAFT dataset with one OWS NamedApi and one persisted layer, plus the layer's sink. */
  private record OwsFixture(DataSet dataSet, DataSink sink) {}

  private OwsFixture datasetWithOwsNamedApiAndLayer() {
    DataSet ds = portalData.dataSet();
    ds.setNamedApis(
        List.of(
            NamedApi.builder().name("OWS Service").slug("ows").standard(ApiStandard.OWS).build()));
    ds = portalData.saveDataSet(ds);

    Pipeline pipeline = portalData.pipeline(ds);
    DataSink sink = portalData.dataSink(ds, pipeline);
    portalData.layer(ds, sink);
    return new OwsFixture(ds, sink);
  }

  private long remainingLayers(UUID datasetId) {
    ResponseEntity<JsonNode> resp =
        restTemplate.exchange(
            "/datasets/" + datasetId + "/layers",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            JsonNode.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    return resp.getBody().get("totalElements").asLong();
  }

  private ResponseEntity<JsonNode> patchNamedApis(UUID datasetId, Object namedApis) {
    return restTemplate.exchange(
        "/datasets/" + datasetId,
        HttpMethod.PATCH,
        new HttpEntity<>(Map.of("namedApis", namedApis), authHeaders()),
        JsonNode.class);
  }

  @Test
  @DisplayName("PATCH removing the last OWS NamedApi clears the dataset's layers")
  void removingLastOwsNamedApiClearsLayers() {
    OwsFixture fixture = datasetWithOwsNamedApiAndLayer();
    UUID id = fixture.dataSet().getId();
    assertThat(remainingLayers(id)).isEqualTo(1);

    ResponseEntity<JsonNode> patch = patchNamedApis(id, List.of());
    assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(remainingLayers(id)).isZero();
    assertThat(layerRepository.existsByDataSinkId(fixture.sink().getId())).isFalse();
  }

  @Test
  @DisplayName("PATCH that keeps an OWS NamedApi preserves the layers")
  void keepingOwsNamedApiPreservesLayers() {
    OwsFixture fixture = datasetWithOwsNamedApiAndLayer();
    UUID id = fixture.dataSet().getId();

    ResponseEntity<JsonNode> patch =
        patchNamedApis(
            id, List.of(Map.of("name", "OWS Service renamed", "slug", "ows", "standard", "OWS")));
    assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(remainingLayers(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("PATCH with two OWS named APIs is accepted — no per-standard limit")
  void multipleOwsNamedApisAreAccepted() {
    DataSet ds = portalData.dataSet();
    UUID id = ds.getId();

    ResponseEntity<JsonNode> patch =
        patchNamedApis(
            id,
            List.of(
                Map.of("name", "OWS One", "slug", "ows", "standard", "OWS"),
                Map.of("name", "OWS Two", "slug", "ows2", "standard", "OWS")));

    assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("PATCH with one OWS and one STA named API is accepted")
  void owsAndStaNamedApisCoexist() {
    DataSet ds = portalData.dataSet();
    UUID id = ds.getId();

    ResponseEntity<JsonNode> patch =
        patchNamedApis(
            id,
            List.of(
                Map.of("name", "Map Service", "slug", "ows", "standard", "OWS"),
                Map.of("name", "Sensor Service", "slug", "sta", "standard", "STA")));

    assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName(
      "PATCH replacing the OWS API with a differently-slugged OWS API preserves the layers")
  void replacingOwsWithDifferentSlugPreservesLayers() {
    OwsFixture fixture = datasetWithOwsNamedApiAndLayer();
    UUID id = fixture.dataSet().getId();

    // Whole-array replace: the old 'ows' entry is removed and a new 'ows-v2' entry is added. Since
    // an OWS API still exists afterwards, the layers must survive — proving the cleanup keys on the
    // standard, not on the slug of the removed entry.
    ResponseEntity<JsonNode> patch =
        patchNamedApis(
            id, List.of(Map.of("name", "OWS Service", "slug", "ows-v2", "standard", "OWS")));
    assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(remainingLayers(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("PATCH dropping only the STA API keeps the layers while an OWS API remains")
  void droppingStaKeepsLayersWhileOwsRemains() {
    OwsFixture fixture = datasetWithOwsNamedApiAndLayer();
    UUID id = fixture.dataSet().getId();
    patchNamedApis(
        id,
        List.of(
            Map.of("name", "OWS Service", "slug", "ows", "standard", "OWS"),
            Map.of("name", "Sensor Service", "slug", "sta", "standard", "STA")));
    assertThat(remainingLayers(id)).isEqualTo(1);

    // Drop the STA, keep the OWS: layers stay because the cleanup is OWS-specific, not
    // "any named API removed".
    ResponseEntity<JsonNode> patch =
        patchNamedApis(
            id, List.of(Map.of("name", "OWS Service", "slug", "ows", "standard", "OWS")));
    assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(remainingLayers(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("PATCH removing one of two OWS APIs keeps layers; removing the last clears them")
  void layersSurviveUntilTheLastOwsApiIsRemoved() {
    OwsFixture fixture = datasetWithOwsNamedApiAndLayer();
    UUID id = fixture.dataSet().getId();
    patchNamedApis(
        id,
        List.of(
            Map.of("name", "OWS One", "slug", "ows", "standard", "OWS"),
            Map.of("name", "OWS Two", "slug", "ows2", "standard", "OWS")));
    assertThat(remainingLayers(id)).isEqualTo(1);

    // One OWS remains → layers stay.
    patchNamedApis(id, List.of(Map.of("name", "OWS Two", "slug", "ows2", "standard", "OWS")));
    assertThat(remainingLayers(id)).isEqualTo(1);

    // Last OWS removed → layers cleared.
    patchNamedApis(id, List.of());
    assertThat(remainingLayers(id)).isZero();
  }
}
