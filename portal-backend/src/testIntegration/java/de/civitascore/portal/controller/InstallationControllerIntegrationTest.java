package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link InstallationController}: the install of a package through the API,
 * with the database, the registry and the record of the install.
 */
@DisplayName("Installations")
class InstallationControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String PACKAGE_ID = "tree-cadastre";
  private static final String SCHEMA = "https://json-schema.org/draft/2020-12/schema";
  private static final String DATASOURCE_SCHEMA =
      "https://civitasconnect.digital/core/datasource/v1";

  /** The UUID of a Data structure on another instance. */
  private static final String FOREIGN_UUID = "0b9e5b0e-6a3f-4c1e-9d2a-7f3c2e1a5b4d";

  /** A Data structure URN of another instance, as a package from that instance carries it. */
  private static final String FOREIGN_URN =
      "urn:core:platform:civitas:datastructure:common:" + FOREIGN_UUID;

  /** The UUID of an Element on another instance. */
  private static final String FOREIGN_ELEMENT_UUID = "5d1c7a2e-3b4f-4a6d-8e9f-0a1b2c3d4e5f";

  /** Identities in the form of the catalogue, which are no CORE URNs of an instance. */
  private static final String SOURCE_URN =
      "urn:core:standard:test:datasource:environment:treedb:al7cx8sr5i";

  private static final String FEED_URN =
      "urn:core:standard:test:datasource:environment:airfeed:i9071kflgc";
  private static final String DATASET_URN =
      "urn:core:standard:test:dataset:environment:trees:q1w2e3r4t5";
  private static final String API_URN = "urn:core:standard:test:api:environment:map:y6u7i8o9p0";

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private InstallationRepository installationRepository;
  @Autowired private DataStructureRepository dataStructureRepository;
  @Autowired private ModelRegistryGateway registry;

  @AfterEach
  void cleanup() {
    installationRepository.deleteAll();
    portalData.cleanAll();
  }

  @Test
  @DisplayName("POST installs the Data structure under a URN of this instance and keeps it DRAFT")
  void install_whenPackageHasOneDataStructure_returnsTheRecord() {
    ResponseEntity<String> installed =
        post(request(PACKAGE_ID, member("Tree cadastre", treeModel())));

    assertThat(installed.getStatusCode())
        .as("%s", installed.getBody())
        .isEqualTo(HttpStatus.CREATED);
    JsonNode body = json(installed.getBody());
    assertThat(body.get("packageId").asString()).isEqualTo(PACKAGE_ID);
    JsonNode line = onlyLine(body);
    UUID shellId = UUID.fromString(line.get("shellId").asString());
    assertThat(line.get("artifactType").asString()).isEqualTo("DATA_STRUCTURE");
    assertThat(line.get("origin").asString()).isEqualTo(FOREIGN_URN);
    assertThat(line.get("urn").asString())
        .contains(shellId.toString())
        .doesNotContain(FOREIGN_UUID);
    assertThat(line.get("versionedUrn").asString()).startsWith(line.get("urn").asString());
    assertThat(dataStructureRepository.findById(shellId).orElseThrow().getDataStructureStatus())
        .isEqualTo(DataStructureStatus.DRAFT);
    assertThat(get("/installations/" + body.get("id").asString()).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("POST keeps the references between the members of a Data structure")
  void install_whenMembersReferenceEachOther_storesTheReferencesToTheCopies() {
    ResponseEntity<String> installed =
        post(request(PACKAGE_ID, member("Tree cadastre", treeModel())));
    assertThat(installed.getStatusCode())
        .as("%s", installed.getBody())
        .isEqualTo(HttpStatus.CREATED);
    JsonNode line = onlyLine(json(installed.getBody()));

    Map<String, Object> root =
        registry.fetchStoredModel(line.get("urn").asString()).orElseThrow().content();
    String treeReference = referenceOf(root, "tree");
    Map<String, Object> tree =
        registry.fetchStoredModel(registry.logicalUrn(treeReference)).orElseThrow().content();
    String locationReference = referenceOf(tree, "location");

    assertThat(treeReference).startsWith("urn:core:").doesNotContain(FOREIGN_ELEMENT_UUID);
    assertThat(locationReference).startsWith("urn:core:");
    assertThat(registry.fetchStoredModel(registry.logicalUrn(locationReference))).isPresent();
  }

  @Test
  @DisplayName("POST refuses a second install of the same package")
  void install_whenPackageIsInstalled_returnsConflict() {
    assertThat(post(request(PACKAGE_ID, member("Tree cadastre", treeModel()))).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    long structures = dataStructureRepository.count();

    ResponseEntity<String> again = post(request(PACKAGE_ID, member("Tree cadastre", treeModel())));

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(json(again.getBody()).get("status").asInt()).isEqualTo(409);
    assertThat(installationRepository.count()).isEqualTo(1);
    assertThat(dataStructureRepository.count()).isEqualTo(structures);
  }

  @Test
  @DisplayName("GET returns the installations, the newest first")
  void getAll_whenTwoPackagesAreInstalled_returnsTheNewestFirst() {
    assertThat(post(request(PACKAGE_ID, member("Tree cadastre", treeModel()))).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(post(request("park-register", member("Park register", parkModel()))).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> page = get("/installations");

    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode content = json(page.getBody()).get("content");
    assertThat(content.size()).isEqualTo(2);
    assertThat(content.get(0).get("packageId").asString()).isEqualTo("park-register");
    assertThat(content.get(1).get("packageId").asString()).isEqualTo(PACKAGE_ID);
    assertThat(content.get(1).get("artifacts").size()).isEqualTo(1);
  }

  @Test
  @DisplayName("POST refuses a package with a kind of member that the install does not know")
  void install_whenMemberKindIsUnknown_returnsBadRequestAndCreatesNothing() {
    long structures = dataStructureRepository.count();
    Map<String, Object> dashboard = new LinkedHashMap<>(member("Tree dashboard", treeModel()));
    dashboard.put("kind", "dashboard");

    ResponseEntity<String> refused =
        post(request(PACKAGE_ID, member("Tree cadastre", treeModel()), dashboard));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(installationRepository.existsByPackageId(PACKAGE_ID)).isFalse();
    assertThat(dataStructureRepository.count()).isEqualTo(structures);
  }

  @Test
  @DisplayName("The database refuses a second record of the same package")
  void saveAndFlush_whenPackageIdExists_throwsDataIntegrityViolation() {
    installationRepository.saveAndFlush(installationOf(PACKAGE_ID));

    // Two installs that run at the same time both pass the check. Then the index decides.
    assertThatThrownBy(() -> installationRepository.saveAndFlush(installationOf(PACKAGE_ID)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "POST installs Data sources and a Dataset with a named API in the Data pool, as DRAFT")
  void install_whenPackageHasDataSourcesAndADataSet_createsThemAsDraftsInTheDatapool() {
    DataPool pool = portalData.dataPool();

    ResponseEntity<String> installed =
        post(
            request(
                pool.getId(),
                PACKAGE_ID,
                member("Tree cadastre", treeModel()),
                sqlSource(),
                mqttSource(Map.of()),
                dataSet(),
                treeMap()));

    assertThat(installed.getStatusCode())
        .as("%s", installed.getBody())
        .isEqualTo(HttpStatus.CREATED);
    List<JsonNode> lines = linesOf(installed);
    assertThat(lines)
        .extracting(line -> line.get("artifactType").asString())
        .containsExactly("DATA_STRUCTURE", "DATA_SOURCE", "DATA_SOURCE", "DATA_SET", "NAMED_API");
    JsonNode structure = lines.get(0);

    JsonNode source = json(get("/datasources/" + lines.get(1).get("shellId").asString()).getBody());
    assertThat(source.get("dataSourceStatus").asString()).isEqualTo("DRAFT");
    assertThat(source.at("/dataStructureVersion/dataStructureId").asString())
        .isEqualTo(structure.get("shellId").asString());
    assertThat(source.at("/dataStructureVersion/modelUrn").asString())
        .isEqualTo(structure.get("versionedUrn").asString());
    assertThat(source.at("/datapoolScope/type").asString()).isEqualTo("SPECIFIC");
    assertThat(source.at("/datapoolScope/datapoolIds/0").asString())
        .isEqualTo(pool.getId().toString());
    assertThat(source.at("/connector/connectorType").asString()).isEqualTo("SQL");
    assertThat(source.at("/connector/password").asString()).isEqualTo("********");
    assertThat(lines.get(1).get("origin").asString()).isEqualTo(SOURCE_URN);
    assertThat(lines.get(1).get("urn").asString()).startsWith("urn:core:");

    JsonNode dataSet = json(get("/datasets/" + lines.get(3).get("shellId").asString()).getBody());
    assertThat(dataSet.get("dataSetStatus").asString()).isEqualTo("DRAFT");
    assertThat(dataSet.at("/datapool/id").asString()).isEqualTo(pool.getId().toString());
    assertThat(dataSet.get("openDataAccess").asBoolean()).isFalse();
    assertThat(dataSet.at("/namedApis/0/slug").asString()).isEqualTo("map");
    assertThat(dataSet.at("/namedApis/0/standard").asString()).isEqualTo("OWS");
    assertThat(lines.get(4).get("origin").asString()).isEqualTo(API_URN);
  }

  @Test
  @DisplayName("POST refuses a package with a Data source but without a Data pool")
  void install_whenDatapoolIsMissing_returnsBadRequestAndCreatesNothing() {
    long structures = dataStructureRepository.count();

    ResponseEntity<String> refused =
        post(request(PACKAGE_ID, member("Tree cadastre", treeModel()), sqlSource()));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody()).contains("datapoolId");
    assertThat(installationRepository.existsByPackageId(PACKAGE_ID)).isFalse();
    assertThat(dataStructureRepository.count()).isEqualTo(structures);
  }

  @Test
  @DisplayName("POST refuses a connector key that the platform does not know, and names the key")
  void install_whenConnectorHasAnUnknownKey_returnsBadRequestWithTheKeyPath() {
    DataPool pool = portalData.dataPool();
    long structures = dataStructureRepository.count();

    ResponseEntity<String> refused =
        post(
            request(
                pool.getId(),
                PACKAGE_ID,
                member("Tree cadastre", treeModel()),
                mqttSource(Map.of("client_id", "air-quality"))));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody()).contains("connector.client_id");
    assertThat(installationRepository.existsByPackageId(PACKAGE_ID)).isFalse();
    assertThat(dataStructureRepository.count()).isEqualTo(structures);
  }

  /**
   * A Data structure from another instance. The root and the member {@code Tree} carry the URNs of
   * that instance. {@code Tree} references {@code Location} in the same document.
   */
  private static Map<String, Object> treeModel() {
    return Map.of(
        "$schema",
        SCHEMA,
        "$id",
        FOREIGN_URN,
        "title",
        "Tree cadastre",
        "type",
        "object",
        "properties",
        Map.of("tree", Map.of("$ref", "#/$defs/Tree")),
        "$defs",
        Map.of(
            "Tree",
            Map.of(
                "$id",
                "urn:core:platform:civitas:element:common:" + FOREIGN_ELEMENT_UUID,
                "type",
                "object",
                "properties",
                Map.of("location", Map.of("$ref", "#/$defs/Location"))),
            "Location",
            Map.of("type", "object", "properties", Map.of("district", Map.of("type", "string")))));
  }

  private static Map<String, Object> parkModel() {
    return Map.of(
        "$schema",
        SCHEMA,
        "title",
        "Park register",
        "type",
        "object",
        "properties",
        Map.of("name", Map.of("type", "string")));
  }

  /**
   * An SQL Data source as a package carries it: the connector with a secret in clear text, and the
   * Data structure by its identity in the package.
   */
  private static Map<String, Object> sqlSource() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("$schema", DATASOURCE_SCHEMA);
    document.put("id", SOURCE_URN);
    document.put("title", "Tree database");
    document.put("description", "Reads the trees from the database of the city");
    document.put("connectionType", "sql");
    document.put("element", FOREIGN_URN);
    document.put("driver", "postgres");
    document.put("dsn", "postgres://trees-db:5432/city");
    document.put("table", "cadastre.trees");
    document.put("columns", List.of("tree_id", "species"));
    document.put("user", "cadastre");
    document.put("password", "cadastre");
    return member("datasource", SOURCE_URN, null, null, document);
  }

  /** An MQTT Data source as a package carries it, with the other keys added to the document. */
  private static Map<String, Object> mqttSource(Map<String, Object> otherKeys) {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("$schema", DATASOURCE_SCHEMA);
    document.put("id", FEED_URN);
    document.put("title", "Air quality feed");
    document.put("description", "Receives the measurements of the stations from the broker");
    document.put("connectionType", "mqtt");
    document.put("element", FOREIGN_URN);
    document.put("urls", List.of("tcp://broker:1883"));
    document.put("topics", List.of("air-quality/+"));
    document.put("qos", 1);
    document.putAll(otherKeys);
    return member("datasource", FEED_URN, null, null, document);
  }

  private static Map<String, Object> dataSet() {
    return member(
        "dataset",
        DATASET_URN,
        "Street trees",
        "Street trees of the district",
        Map.of("openDataAccess", false));
  }

  private static Map<String, Object> treeMap() {
    return member(
        "api",
        API_URN,
        "Tree map",
        null,
        Map.of(
            "name",
            "Tree map",
            "slug",
            "map",
            "standard",
            "OWS",
            "description",
            "The street trees as a map service"));
  }

  private static Map<String, Object> member(String name, Map<String, Object> model) {
    return member("datastructure", FOREIGN_URN, name, "Installed by the integration test", model);
  }

  private static Map<String, Object> member(
      String kind, String urn, String name, String description, Map<String, Object> content) {
    Map<String, Object> member = new LinkedHashMap<>();
    member.put("kind", kind);
    member.put("urn", urn);
    if (name != null) {
      member.put("name", name);
    }
    if (description != null) {
      member.put("description", description);
    }
    member.put("content", content);
    return member;
  }

  @SafeVarargs
  private static Map<String, Object> request(String packageId, Map<String, Object>... members) {
    return request(null, packageId, members);
  }

  @SafeVarargs
  private static Map<String, Object> request(
      UUID datapoolId, String packageId, Map<String, Object>... members) {
    Map<String, Object> request = new LinkedHashMap<>();
    if (datapoolId != null) {
      request.put("datapoolId", datapoolId.toString());
    }
    request.put(
        "package", Map.of("id", packageId, "version", "1.0.0", "members", List.of(members)));
    return request;
  }

  private static Installation installationOf(String packageId) {
    Installation installation = new Installation();
    installation.setPackageId(packageId);
    installation.setPackageVersion("1.0.0");
    return installation;
  }

  private static JsonNode onlyLine(JsonNode installation) {
    JsonNode lines = installation.get("artifacts");
    assertThat(lines.size()).isEqualTo(1);
    return lines.get(0);
  }

  private static List<JsonNode> linesOf(ResponseEntity<String> installed) {
    List<JsonNode> lines = new ArrayList<>();
    json(installed.getBody()).get("artifacts").forEach(lines::add);
    return lines;
  }

  /** The {@code $ref} of a property of a stored document. */
  @SuppressWarnings("unchecked")
  private static String referenceOf(Map<String, Object> document, String property) {
    Map<String, Object> properties = (Map<String, Object>) document.get("properties");
    return (String) ((Map<String, Object>) properties.get(property)).get("$ref");
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<String> post(Object body) {
    return restTemplate.exchange(
        "/installations", HttpMethod.POST, new HttpEntity<>(body, authHeaders()), String.class);
  }

  private ResponseEntity<String> get(String path) {
    return restTemplate.exchange(
        path, HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
  }

  private static JsonNode json(String body) {
    return new ObjectMapper().readTree(body);
  }
}
