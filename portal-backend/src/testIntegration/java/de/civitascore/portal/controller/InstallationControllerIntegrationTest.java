package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.ObjectMapper;

/**
 * The install path as a caller meets it: one package with each kind of member goes through the
 * installations resource and out again. The unit tests cover the rules of each member. This test
 * shows that the services, the registry and the journal agree, in one transaction each way.
 */
@DisplayName("Installations, through the routes a caller reaches")
class InstallationControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String PACKAGE_ID = "urn:core:package:openurbanapps:soil_moisture";
  private static final String STRUCTURE_URN =
      "urn:core:standard:openurbanapps:datastructure:environment:soil_moisture:demo";
  private static final String ELEMENT_URN =
      "urn:core:standard:openurbanapps:element:environment:soil_moisture_reading:demo";
  private static final String SOURCE_URN =
      "urn:core:standard:openurbanapps:datasource:environment:soil_sensor_db:demo";
  private static final String DATASET_URN =
      "urn:core:standard:openurbanapps:dataset:environment:soil_moisture:demo";
  private static final String MAPPING_URN =
      "urn:core:standard:openurbanapps:mapping:environment:soil_to_table:demo";
  private static final String SINK_URN =
      "urn:core:standard:openurbanapps:datasink:environment:soil_moisture_table:demo";
  private static final String PIPELINE_URN =
      "urn:core:standard:openurbanapps:pipeline:environment:soil_moisture_ingest:demo";

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private InstallationRepository installationRepository;
  @Autowired private DataStructureRepository dataStructureRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private ModelRegistryGateway registry;

  private UUID datapoolId;

  @BeforeEach
  void setUp() {
    datapoolId = portalData.dataPool().getId();
  }

  @AfterEach
  void cleanup() {
    installationRepository.deleteAll();
    portalData.cleanAll();
  }

  @Test
  @DisplayName("installs each member and records what it became")
  void installsEachMemberAndRecordsWhatItBecame() {
    ResponseEntity<String> installed = post("/installations", request(STRUCTURE_URN));

    assertThat(installed.getStatusCode())
        .as("%s", installed.getBody())
        .isEqualTo(HttpStatus.CREATED);
    Map<String, JsonNode> lines = linesByType(installed.getBody());
    assertThat(lines.keySet())
        .containsExactly(
            "DATA_STRUCTURE", "DATA_SOURCE", "DATA_SET", "MAPPING", "DATA_SINK", "PIPELINE");
    assertThat(lines.values())
        .extracting(line -> line.get("origin").asText())
        .containsExactly(
            STRUCTURE_URN, SOURCE_URN, DATASET_URN, MAPPING_URN, SINK_URN, PIPELINE_URN);
    assertThat(lines.values())
        .allSatisfy(line -> assertThat(line.get("action").asText()).isEqualTo("CREATED"));
    assertThat(dataStructureRepository.existsById(shellOf(lines.get("DATA_STRUCTURE")))).isTrue();
    assertThat(dataSourceRepository.existsById(shellOf(lines.get("DATA_SOURCE")))).isTrue();
    assertThat(dataSetRepository.existsById(shellOf(lines.get("DATA_SET")))).isTrue();
    assertThat(registry.fetchPayload(versionOf(lines.get("PIPELINE")))).isPresent();
    assertThat(get("/installations/" + field(installed.getBody(), "id")).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("refuses a second install of the same package")
  void refusesASecondInstallOfTheSamePackage() {
    long before = installationRepository.count();
    assertThat(post("/installations", request(STRUCTURE_URN)).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> again = post("/installations", request(STRUCTURE_URN));

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(installationRepository.count()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName("leaves nothing behind when a member references what the package does not ship")
  void leavesNothingBehindWhenAReferenceDoesNotResolve() {
    long structures = dataStructureRepository.count();
    long sources = dataSourceRepository.count();
    long dataSets = dataSetRepository.count();

    ResponseEntity<String> refused =
        post(
            "/installations",
            request(
                "urn:core:standard:openurbanapps:datastructure:environment:not_in_this_package:demo"));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    // The structure, the source and the dataset are created before the mapping is reached. The
    // refusal has to take them back.
    assertThat(installationRepository.existsByPackageIdAndUninstalledAtIsNull(PACKAGE_ID))
        .isFalse();
    assertThat(dataStructureRepository.count()).isEqualTo(structures);
    assertThat(dataSourceRepository.count()).isEqualTo(sources);
    assertThat(dataSetRepository.count()).isEqualTo(dataSets);
  }

  @Test
  @DisplayName("uninstalls what the install created, keeps the record and frees the package")
  void uninstallsWhatTheInstallCreatedAndKeepsTheRecord() {
    ResponseEntity<String> installed = post("/installations", request(STRUCTURE_URN));
    assertThat(installed.getStatusCode())
        .as("%s", installed.getBody())
        .isEqualTo(HttpStatus.CREATED);
    String id = field(installed.getBody(), "id");
    Map<String, JsonNode> lines = linesByType(installed.getBody());

    ResponseEntity<String> uninstalled = delete("/installations/" + id);

    assertThat(uninstalled.getStatusCode())
        .as("%s", uninstalled.getBody())
        .isEqualTo(HttpStatus.NO_CONTENT);
    JsonNode read = json(get("/installations/" + id).getBody());
    JsonNode uninstalledAt = read.get("uninstalledAt");
    assertThat(uninstalledAt != null && !uninstalledAt.isNull())
        .as("uninstalledAt is set")
        .isTrue();
    assertThat(read.get("artifacts").size()).isEqualTo(6);
    assertThat(dataSetRepository.existsById(shellOf(lines.get("DATA_SET")))).isFalse();
    assertThat(dataSourceRepository.existsById(shellOf(lines.get("DATA_SOURCE")))).isFalse();
    assertThat(dataStructureRepository.existsById(shellOf(lines.get("DATA_STRUCTURE")))).isFalse();
    assertThat(registry.fetchPayload(versionOf(lines.get("PIPELINE")))).isEmpty();
    // The package is free again. The uninstalled installation stays as it is.
    assertThat(post("/installations", request(STRUCTURE_URN)).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(delete("/installations/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<String> post(String path, Object body) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(body, authHeaders()), String.class);
  }

  private ResponseEntity<String> get(String path) {
    return restTemplate.exchange(
        path, HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
  }

  private ResponseEntity<String> delete(String path) {
    return restTemplate.exchange(
        path, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
  }

  private static JsonNode json(String body) {
    return new ObjectMapper().readTree(body);
  }

  private static String field(String body, String name) {
    return json(body).get(name).asText();
  }

  /** The artifact lines of an installation by type, in the order the install wrote them. */
  private static Map<String, JsonNode> linesByType(String body) {
    Map<String, JsonNode> byType = new LinkedHashMap<>();
    JsonNode lines = json(body).get("artifacts");
    for (int i = 0; i < lines.size(); i++) {
      byType.put(lines.get(i).get("artifactType").asText(), lines.get(i));
    }
    return byType;
  }

  private static UUID shellOf(JsonNode line) {
    return UUID.fromString(line.get("shellId").asText());
  }

  private static String versionOf(JsonNode line) {
    return line.get("versionedUrn").asText();
  }

  /**
   * The package of the Bruno flow: each kind once, listed out of order on purpose. The mapping
   * reads from {@code mappingSource}, which is the package's own structure unless a test points it
   * elsewhere.
   */
  private Map<String, Object> request(String mappingSource) {
    return Map.of(
        "datapoolId",
        datapoolId.toString(),
        "package",
        Map.of(
            "id",
            PACKAGE_ID,
            "version",
            "1.0.0",
            "title",
            "Soil moisture, end to end",
            "members",
            List.of(
                member(
                    "pipeline",
                    PIPELINE_URN,
                    "Soil moisture ingest",
                    Map.of(
                        "nodes",
                        List.of(
                            Map.of("id", "start", "kind", "start"),
                            Map.of("id", "read", "kind", "source", "sourceRef", SOURCE_URN),
                            Map.of("id", "map", "kind", "mapping", "mappingRef", MAPPING_URN),
                            Map.of("id", "write", "kind", "sink", "sinkRef", SINK_URN),
                            Map.of("id", "end", "kind", "end")),
                        "edges",
                        List.of(
                            Map.of("id", "e1", "source", "start", "target", "read"),
                            Map.of("id", "e2", "source", "read", "target", "map"),
                            Map.of("id", "e3", "source", "map", "target", "write"),
                            Map.of("id", "e4", "source", "write", "target", "end")))),
                member(
                    "datasink",
                    SINK_URN,
                    "Soil moisture table",
                    Map.of(
                        "connectionType",
                        "postgis",
                        "tableName",
                        "soil_moisture",
                        "element",
                        STRUCTURE_URN)),
                member(
                    "mapping",
                    MAPPING_URN,
                    "Soil to table",
                    Map.of(
                        "source",
                        mappingSource,
                        "target",
                        STRUCTURE_URN,
                        "fields",
                        Map.of(
                            "$.tensionKpa",
                            Map.of("op", "copy", "sourcePath", "$.tensionKpa"),
                            "$.measuredAt",
                            Map.of("op", "copy", "sourcePath", "$.measuredAt")))),
                member(
                    "datasource",
                    SOURCE_URN,
                    "Soil sensor DB",
                    Map.of(
                        "connectionType",
                        "sql",
                        "element",
                        STRUCTURE_URN,
                        "driver",
                        "postgres",
                        "dsn",
                        "jdbc:postgresql://postgis:5432/core",
                        "table",
                        "soil_readings",
                        "columns",
                        List.of("tensionKpa", "measuredAt"),
                        "user",
                        "core",
                        "password",
                        "not-a-real-password")),
                member(
                    "dataset",
                    DATASET_URN,
                    "Soil moisture readings",
                    Map.of("openDataAccess", false)),
                member(
                    "datastructure",
                    STRUCTURE_URN,
                    "Soil moisture",
                    Map.of(
                        "$schema",
                        "https://json-schema.org/draft/2020-12/schema",
                        "$id",
                        STRUCTURE_URN,
                        "title",
                        "Soil moisture",
                        "$defs",
                        Map.of(
                            "Reading",
                            Map.of(
                                "$id",
                                ELEMENT_URN,
                                "type",
                                "object",
                                "properties",
                                Map.of(
                                    "tensionKpa",
                                    Map.of("type", "number"),
                                    "measuredAt",
                                    Map.of("type", "string", "format", "date-time")),
                                "required",
                                List.of("tensionKpa", "measuredAt"))))))));
  }

  private static Map<String, Object> member(String kind, String urn, String name, Object content) {
    return Map.of(
        "kind",
        kind,
        "urn",
        urn,
        "name",
        name,
        "description",
        "Installed by the integration test",
        "content",
        content);
  }
}
