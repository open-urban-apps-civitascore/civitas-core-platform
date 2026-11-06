package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkState;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

@DisplayName("Removing Data sinks with their Layers and APIs")
class DataSinkRemovalIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String CONFIRMED = "?confirmDataLoss=true";
  private static final String RESOURCE_IN_USE = "urn:civitas:error:RESOURCE_IN_USE";
  private static final String REMOVAL_NOT_CONFIRMED = "urn:civitas:error:REMOVAL_NOT_CONFIRMED";

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private DataSinkRepository dataSinkRepository;
  @Autowired private LayerRepository layerRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private ModelRegistryGateway modelRegistryGateway;

  private DataSet dataSet;
  private Pipeline pipeline;

  @BeforeEach
  void draftDataSetWithPipeline() {
    dataSet = portalData.dataSet();
    pipeline = portalData.pipeline(dataSet);
  }

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  /** A PostGIS sink, published by an OWS API through one Layer. */
  private record MapSurface(DataSink sink, NamedApi ows, Layer layer) {}

  private MapSurface mapSurface(boolean provisioned) {
    return mapSurface(pipeline, provisioned);
  }

  private MapSurface mapSurface(Pipeline linkedTo, boolean provisioned) {
    DataSink sink = postgisSink(linkedTo, provisioned);
    NamedApi ows = addApi(ApiStandard.OWS);
    Layer layer = portalData.layer(dataSet, sink, ows);
    return new MapSurface(sink, ows, layer);
  }

  private DataSink postgisSink(Pipeline linkedTo, boolean provisioned) {
    return portalData.dataSink(
        dataSet,
        linkedTo,
        sink -> {
          sink.setDataSinkType(DataSinkType.POSTGIS);
          sink.setProvisioned(provisioned);
        });
  }

  private DataSink frostSink(Pipeline linkedTo, boolean provisioned) {
    return portalData.dataSink(dataSet, linkedTo, sink -> sink.setProvisioned(provisioned));
  }

  private DataSink pendingSink() {
    return portalData.dataSink(
        dataSet,
        null,
        sink -> {
          sink.setProvisioned(true);
          sink.markPendingUnprovision(null);
        });
  }

  private NamedApi addApi(ApiStandard standard) {
    return portalData.namedApi(dataSet, standard);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, Object body) {
    return restTemplate.exchange(path, method, new HttpEntity<>(body, headers()), JsonNode.class);
  }

  private String pipelinePath() {
    return "/datasets/" + dataSet.getId() + "/pipelines/" + pipeline.getId();
  }

  private String sinkPath(DataSink sink) {
    return "/datasets/" + dataSet.getId() + "/datasinks/" + sink.getId();
  }

  private Map<String, Object> pipelineBody(UUID... dataSinkIds) {
    return Map.of("name", pipeline.getName(), "dataSinkIds", List.of(dataSinkIds));
  }

  private List<String> apiStandards() {
    JsonNode body = exchange(HttpMethod.GET, "/datasets/" + dataSet.getId(), null).getBody();
    return StreamSupport.stream(body.get("namedApis").spliterator(), false)
        .map(api -> api.get("standard").asString())
        .toList();
  }

  private DataSinkState stateOf(DataSink sink) {
    return dataSinkRepository.findById(sink.getId()).orElseThrow().getState();
  }

  private static List<String> ids(JsonNode node, String field) {
    return StreamSupport.stream(node.get(field).spliterator(), false)
        .map(entry -> entry.get("id").asString())
        .toList();
  }

  private static void assertDataLossUnconfirmed(ResponseEntity<JsonNode> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("type").asString()).isEqualTo(REMOVAL_NOT_CONFIRMED);
    assertThat(response.getBody().get("detail").asString()).contains("confirmDataLoss=true");
  }

  @Nested
  @DisplayName("Pipeline delete")
  class PipelineDelete {

    @Test
    @DisplayName("of a provisioned sink without confirmation answers 409 and changes nothing")
    void unconfirmed_answers409AndChangesNothing() {
      MapSurface surface = mapSurface(true);

      ResponseEntity<JsonNode> response = exchange(HttpMethod.DELETE, pipelinePath(), null);

      assertDataLossUnconfirmed(response);
      JsonNode removal = response.getBody().get("removal");
      assertThat(removal.get("dataSinks").get(0).properties())
          .extracting(Map.Entry::getKey)
          .containsExactlyInAnyOrder("id", "dataSinkType", "provisioned");
      assertThat(ids(removal, "dataSinks")).containsExactly(surface.sink().getId().toString());
      assertThat(removal.get("dataSinks").get(0).get("dataSinkType").asString())
          .isEqualTo("POSTGIS");
      assertThat(removal.get("dataSinks").get(0).get("provisioned").asBoolean()).isTrue();
      assertThat(removal.get("layers").get(0).properties())
          .extracting(Map.Entry::getKey)
          .containsExactlyInAnyOrder("id", "layerName", "title");
      assertThat(ids(removal, "layers")).containsExactly(surface.layer().getId().toString());
      assertThat(removal.get("layers").get(0).get("layerName").asString())
          .isEqualTo(surface.layer().getLayerName());
      assertThat(removal.get("apis").get(0).properties())
          .extracting(Map.Entry::getKey)
          .containsExactlyInAnyOrder("id", "slug", "name", "standard");
      assertThat(ids(removal, "apis")).containsExactly(surface.ows().getId().toString());
      assertThat(removal.get("apis").get(0).get("name").asString())
          .isEqualTo(surface.ows().getName());
      assertThat(pipelineRepository.existsById(pipeline.getId())).isTrue();
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId())).hasSize(1);
      assertThat(layerRepository.existsById(surface.layer().getId())).isTrue();
      assertThat(apiStandards()).containsExactly("OWS");
    }

    @Test
    @DisplayName("with confirmation keeps a provisioned sink pending and deletes its Layer and API")
    void confirmed_pendsProvisionedSinkAndDeletesLayerAndApi() {
      MapSurface surface = mapSurface(true);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, pipelinePath() + CONFIRMED, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(pipelineRepository.existsById(pipeline.getId())).isFalse();
      assertThat(stateOf(surface.sink())).isEqualTo(DataSinkState.PENDING_UNPROVISION);
      assertThat(layerRepository.existsById(surface.layer().getId())).isFalse();
      assertThat(apiStandards()).isEmpty();
      assertThat(exchange(HttpMethod.GET, sinkPath(surface.sink()), null).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
      JsonNode list =
          exchange(HttpMethod.GET, "/datasets/" + dataSet.getId() + "/datasinks", null).getBody();
      assertThat(list.get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName(
        "of an unprovisioned sink with a Layer and API without confirmation answers 409 and"
            + " changes nothing")
    void unconfirmedUnprovisionedSinkWithLayerAndApi_answers409AndChangesNothing() {
      MapSurface surface = mapSurface(false);

      ResponseEntity<JsonNode> response = exchange(HttpMethod.DELETE, pipelinePath(), null);

      assertDataLossUnconfirmed(response);
      assertThat(pipelineRepository.existsById(pipeline.getId())).isTrue();
      assertThat(stateOf(surface.sink())).isEqualTo(DataSinkState.ACTIVE);
      assertThat(layerRepository.existsById(surface.layer().getId())).isTrue();
      assertThat(apiStandards()).containsExactly("OWS");
    }

    @Test
    @DisplayName("without confirmation deletes an unprovisioned sink that has no Layer and no API")
    void unconfirmed_deletesUnprovisionedSinkWithoutLayersAndApis() {
      DataSink sink = frostSink(pipeline, false);

      ResponseEntity<JsonNode> response = exchange(HttpMethod.DELETE, pipelinePath(), null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(pipelineRepository.existsById(pipeline.getId())).isFalse();
      assertThat(dataSinkRepository.existsById(sink.getId())).isFalse();
    }

    @Test
    @DisplayName(
        "with confirmation deletes an unprovisioned sink its definition writes into, with its"
            + " Layer and API")
    void confirmed_deletesUnprovisionedSinkTheDefinitionReferences() {
      MapSurface surface = mapSurface(false);
      DataStructureVersion road =
          portalData.attachModel(
              portalData.dataStructureVersion(portalData.dataStructure()),
              portalData.dataStructureVersionModel("Road"));
      DataSink sink =
          portalData.attachSinkConfiguration(
              surface.sink(), Map.of("tableName", "roads", "element", road.getModelUrn()));
      pipeline =
          portalData.attachPipelineDefinition(
              pipeline,
              Map.of(
                  "nodes",
                  List.of(
                      Map.of(
                          "id",
                          "sink-1",
                          "kind",
                          "sink",
                          "label",
                          "Sink",
                          "sinkRef",
                          sink.getConfigurationUrn())),
                  "edges",
                  List.of()),
              null);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, pipelinePath() + CONFIRMED, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(pipelineRepository.existsById(pipeline.getId())).isFalse();
      assertThat(dataSinkRepository.existsById(sink.getId())).isFalse();
      assertThat(layerRepository.existsById(surface.layer().getId())).isFalse();
      assertThat(apiStandards()).isEmpty();
      assertThat(modelRegistryGateway.fetchPayload(pipeline.getModelUrn())).isEmpty();
      assertThat(modelRegistryGateway.fetchPayload(sink.getConfigurationUrn())).isEmpty();
    }

    @Test
    @DisplayName(
        "with confirmation a provisioned sink keeps only its table, so its Data structure can be"
            + " deleted")
    void confirmed_provisionedSinkFreesItsDataStructure() {
      MapSurface surface = mapSurface(true);
      DataStructure structure = portalData.dataStructure();
      DataStructureVersion road =
          portalData.attachModel(
              portalData.dataStructureVersion(structure),
              portalData.dataStructureVersionModel("Road"));
      DataSink sink =
          portalData.attachSinkConfiguration(
              surface.sink(), Map.of("tableName", "roads", "element", road.getModelUrn()));
      String configurationUrn = sink.getConfigurationUrn();

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, pipelinePath() + CONFIRMED, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      DataSink pending = dataSinkRepository.findById(sink.getId()).orElseThrow();
      assertThat(pending.getState()).isEqualTo(DataSinkState.PENDING_UNPROVISION);
      assertThat(pending.getUnprovisionTableName()).isEqualTo("roads");
      assertThat(pending.getConfigurationUrn()).isNull();
      assertThat(modelRegistryGateway.fetchPayload(configurationUrn)).isEmpty();
      assertThat(
              exchange(HttpMethod.DELETE, "/datastructures/" + structure.getId(), null)
                  .getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("deletes the STA API with the last active FROST sink")
    void lastFrostSink_takesTheStaApi() {
      frostSink(pipeline, true);
      addApi(ApiStandard.STA);
      addApi(ApiStandard.CUSTOM);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, pipelinePath() + CONFIRMED, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(apiStandards()).containsExactly("CUSTOM");
    }
  }

  @Nested
  @DisplayName("Pipeline update")
  class PipelineUpdate {

    @Test
    @DisplayName("PUT removing the last link without confirmation answers 409 and changes nothing")
    void putUnconfirmed_answers409AndChangesNothing() {
      frostSink(pipeline, true);
      addApi(ApiStandard.STA);

      ResponseEntity<JsonNode> response = exchange(HttpMethod.PUT, pipelinePath(), pipelineBody());

      assertDataLossUnconfirmed(response);
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId())).hasSize(1);
      assertThat(pipelineRepository.findById(pipeline.getId()).orElseThrow().getVersion())
          .isEqualTo(pipeline.getVersion());
      assertThat(apiStandards()).containsExactly("STA");
    }

    @Test
    @DisplayName(
        "PATCH removing the last link without confirmation answers 409 and changes nothing")
    void patchUnconfirmed_answers409AndChangesNothing() {
      frostSink(pipeline, true);
      addApi(ApiStandard.STA);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.PATCH, pipelinePath(), Map.of("dataSinkIds", List.of()));

      assertDataLossUnconfirmed(response);
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId())).hasSize(1);
      assertThat(pipelineRepository.findById(pipeline.getId()).orElseThrow().getVersion())
          .isEqualTo(pipeline.getVersion());
      assertThat(apiStandards()).containsExactly("STA");
    }

    @Test
    @DisplayName(
        "PUT removing an unprovisioned sink with an API without confirmation answers 409 and"
            + " changes nothing")
    void putUnconfirmedUnprovisionedSinkWithApi_answers409AndChangesNothing() {
      DataSink sink = frostSink(pipeline, false);
      addApi(ApiStandard.STA);

      ResponseEntity<JsonNode> response = exchange(HttpMethod.PUT, pipelinePath(), pipelineBody());

      assertDataLossUnconfirmed(response);
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId()))
          .extracting(DataSink::getId)
          .containsExactly(sink.getId());
      assertThat(apiStandards()).containsExactly("STA");
    }

    @Test
    @DisplayName("PUT with confirmation deletes an unprovisioned sink and its API")
    void putConfirmedUnprovisionedSinkWithApi_deletesBoth() {
      DataSink sink = frostSink(pipeline, false);
      addApi(ApiStandard.STA);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.PUT, pipelinePath() + CONFIRMED, pipelineBody());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(dataSinkRepository.existsById(sink.getId())).isFalse();
      assertThat(apiStandards()).isEmpty();
    }

    @Test
    @DisplayName("PUT with confirmation removes the link, pends the sink and deletes the API")
    void putConfirmed_removesTheApi() {
      DataSink sink = frostSink(pipeline, true);
      addApi(ApiStandard.STA);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.PUT, pipelinePath() + CONFIRMED, pipelineBody());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(stateOf(sink)).isEqualTo(DataSinkState.PENDING_UNPROVISION);
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId())).isEmpty();
      assertThat(apiStandards()).isEmpty();
    }

    @Test
    @DisplayName("PATCH with confirmation removes the link, pends the sink and deletes the API")
    void patchConfirmed_removesTheApi() {
      DataSink sink = frostSink(pipeline, true);
      addApi(ApiStandard.STA);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.PATCH, pipelinePath() + CONFIRMED, Map.of("dataSinkIds", List.of()));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(stateOf(sink)).isEqualTo(DataSinkState.PENDING_UNPROVISION);
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId())).isEmpty();
      assertThat(apiStandards()).isEmpty();
    }

    @Test
    @DisplayName("an unknown Data sink id answers 400 before the confirmation and stores nothing")
    void unknownDataSinkId_answers400BeforeTheConfirmation() {
      DataSink sink = frostSink(pipeline, true);
      addApi(ApiStandard.STA);
      Map<String, Object> body =
          Map.of(
              "name",
              pipeline.getName(),
              "dataSinkIds",
              List.of(UUID.randomUUID()),
              "model",
              Map.of(
                  "nodes",
                  List.of(
                      Map.of("id", "source-1", "kind", "source"),
                      Map.of("id", "sink-1", "kind", "sink")),
                  "edges",
                  List.of(Map.of("id", "edge-1", "source", "source-1", "target", "sink-1"))));

      ResponseEntity<JsonNode> response = exchange(HttpMethod.PUT, pipelinePath(), body);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      Pipeline stored = pipelineRepository.findById(pipeline.getId()).orElseThrow();
      assertThat(stored.getModelLogicalUrn()).isNull();
      assertThat(stored.getVersion()).isEqualTo(pipeline.getVersion());
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId()))
          .extracting(DataSink::getId)
          .containsExactly(sink.getId());
      assertThat(apiStandards()).containsExactly("STA");
    }

    @Test
    @DisplayName("replacing the sink in one request keeps the API and drops the old Layer")
    void replacement_keepsTheApi() {
      MapSurface surface = mapSurface(true);
      DataSink replacement = postgisSink(null, false);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.PUT, pipelinePath() + CONFIRMED, pipelineBody(replacement.getId()));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(apiStandards()).containsExactly("OWS");
      assertThat(layerRepository.existsById(surface.layer().getId())).isFalse();
      assertThat(stateOf(surface.sink())).isEqualTo(DataSinkState.PENDING_UNPROVISION);
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId()))
          .extracting(DataSink::getId)
          .containsExactly(replacement.getId());
    }
  }

  @Nested
  @DisplayName("Direct Data sink delete")
  class DirectDelete {

    @Test
    @DisplayName("of a sink a Pipeline links answers 409 even when confirmed and changes nothing")
    void linkedSink_answers409AndChangesNothing() {
      MapSurface surface = mapSurface(false);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, sinkPath(surface.sink()) + CONFIRMED, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody().get("type").asString()).isEqualTo(RESOURCE_IN_USE);
      assertThat(response.getBody().get("detail").asString())
          .doesNotContain(pipeline.getId().toString());
      assertThat(dataSinkRepository.findByPipelinesId(pipeline.getId()))
          .extracting(DataSink::getId)
          .containsExactly(surface.sink().getId());
      assertThat(layerRepository.existsById(surface.layer().getId())).isTrue();
      assertThat(apiStandards()).containsExactly("OWS");
    }

    @Test
    @DisplayName("of a provisioned sink without confirmation answers 409 and changes nothing")
    void unconfirmed_answers409() {
      MapSurface surface = mapSurface(null, true);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, sinkPath(surface.sink()), null);

      assertDataLossUnconfirmed(response);
      assertThat(stateOf(surface.sink())).isEqualTo(DataSinkState.ACTIVE);
      assertThat(layerRepository.existsById(surface.layer().getId())).isTrue();
      assertThat(apiStandards()).containsExactly("OWS");
    }

    @Test
    @DisplayName("with confirmation pends a provisioned sink")
    void confirmed_pendsProvisionedSink() {
      MapSurface surface = mapSurface(null, true);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, sinkPath(surface.sink()) + CONFIRMED, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(stateOf(surface.sink())).isEqualTo(DataSinkState.PENDING_UNPROVISION);
      assertThat(layerRepository.existsById(surface.layer().getId())).isFalse();
      assertThat(apiStandards()).isEmpty();
    }

    @Test
    @DisplayName(
        "of an unprovisioned sink with a Layer without confirmation answers 409 and changes"
            + " nothing")
    void unconfirmedUnprovisionedSinkWithLayer_answers409AndChangesNothing() {
      MapSurface surface = mapSurface(null, false);
      postgisSink(null, false);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, sinkPath(surface.sink()), null);

      assertDataLossUnconfirmed(response);
      assertThat(stateOf(surface.sink())).isEqualTo(DataSinkState.ACTIVE);
      assertThat(layerRepository.existsById(surface.layer().getId())).isTrue();
      assertThat(apiStandards()).containsExactly("OWS");
    }

    @Test
    @DisplayName(
        "with confirmation deletes an unprovisioned sink with its Layer; another sink keeps the"
            + " API")
    void confirmedUnprovisionedSink_isDeleted() {
      MapSurface surface = mapSurface(null, false);
      postgisSink(null, false);

      ResponseEntity<JsonNode> response =
          exchange(HttpMethod.DELETE, sinkPath(surface.sink()) + CONFIRMED, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(dataSinkRepository.existsById(surface.sink().getId())).isFalse();
      assertThat(layerRepository.existsById(surface.layer().getId())).isFalse();
      assertThat(apiStandards()).containsExactly("OWS");
    }

    @Test
    @DisplayName("a pending sink is not found")
    void pendingSink_isNotFound() {
      DataSink sink = pendingSink();

      assertThat(exchange(HttpMethod.DELETE, sinkPath(sink), null).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("the list of a Dataset leaves out a pending sink")
    void pendingSink_isNotListed() {
      DataSink active = frostSink(null, false);
      pendingSink();

      JsonNode list =
          exchange(HttpMethod.GET, "/datasets/" + dataSet.getId() + "/datasinks", null).getBody();

      assertThat(ids(list, "content")).containsExactly(active.getId().toString());
    }
  }

  @Nested
  @DisplayName("API rules")
  class ApiRules {

    private ResponseEntity<JsonNode> patchApis(List<Map<String, String>> apis) {
      return exchange(HttpMethod.PATCH, "/datasets/" + dataSet.getId(), Map.of("namedApis", apis));
    }

    private Map<String, String> api(String slug, ApiStandard standard) {
      return Map.of("name", slug, "slug", slug, "standard", standard.name());
    }

    @Test
    @DisplayName("an STA API needs an active FROST sink")
    void staWithoutActiveFrostSink_isRejected() {
      pendingSink();

      assertThat(patchApis(List.of(api("sta", ApiStandard.STA))).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);

      frostSink(null, false);
      assertThat(patchApis(List.of(api("sta", ApiStandard.STA))).getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an OWS API needs an active PostGIS sink")
    void owsWithoutPostgisSink_isRejected() {
      frostSink(null, false);

      assertThat(patchApis(List.of(api("ows", ApiStandard.OWS))).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a Dataset has at most one STA API")
    void secondSta_isRejected() {
      frostSink(null, false);

      assertThat(
              patchApis(List.of(api("traffic", ApiStandard.STA), api("weather", ApiStandard.STA)))
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("CUSTOM APIs need no sink and have no limit")
    void customApis_areFree() {
      assertThat(
              patchApis(List.of(api("one", ApiStandard.CUSTOM), api("two", ApiStandard.CUSTOM)))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("replacing the OWS API under a new slug keeps the Layers linked to it")
    void reslugOws_keepsLayersLinked() {
      MapSurface surface = mapSurface(false);

      ResponseEntity<JsonNode> response = patchApis(List.of(api("maps", ApiStandard.OWS)));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(apiStandards()).containsExactly("OWS");
      assertThat(layerRepository.findById(surface.layer().getId()))
          .get()
          .satisfies(
              layer -> assertThat(layer.getNamedApi().getId()).isNotEqualTo(surface.ows().getId()));
    }
  }

  @Nested
  @DisplayName("Layer and API link")
  class LayerApiLink {

    private ResponseEntity<JsonNode> createLayer(DataSink sink) {
      return exchange(
          HttpMethod.POST,
          "/datasets/" + dataSet.getId() + "/layers",
          Map.of("dataSinkId", sink.getId(), "layerName", "roads"));
    }

    @Test
    @DisplayName("a new Layer is linked to the OWS API and goes with it")
    void layerIsLinkedToTheOwsApi() {
      DataSink sink = postgisSink(pipeline, false);
      NamedApi ows = addApi(ApiStandard.OWS);

      ResponseEntity<JsonNode> created = createLayer(sink);
      assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      UUID layerId = UUID.fromString(created.getBody().get("id").asString());
      assertThat(layerRepository.findByDataSetId(dataSet.getId()))
          .singleElement()
          .satisfies(layer -> assertThat(layer.getNamedApi().getId()).isEqualTo(ows.getId()));

      assertThat(
              exchange(
                      HttpMethod.PATCH,
                      "/datasets/" + dataSet.getId(),
                      Map.of("namedApis", List.of()))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(layerRepository.existsById(layerId)).isFalse();
    }

    @Test
    @DisplayName("a Layer without an OWS API is rejected")
    void layerWithoutOwsApi_isRejected() {
      DataSink sink = postgisSink(pipeline, false);

      assertThat(createLayer(sink).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
