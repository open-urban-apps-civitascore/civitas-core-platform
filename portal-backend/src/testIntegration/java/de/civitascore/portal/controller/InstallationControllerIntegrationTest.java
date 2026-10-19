package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
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
 * Integration tests for {@link InstallationController}: the install of a package of Data structures
 * through the API, with the database, the registry and the record of the install.
 */
@DisplayName("Installations")
class InstallationControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String PACKAGE_ID = "tree-cadastre";
  private static final String SCHEMA = "https://json-schema.org/draft/2020-12/schema";

  /** The UUID of a Data structure on another instance. */
  private static final String FOREIGN_UUID = "0b9e5b0e-6a3f-4c1e-9d2a-7f3c2e1a5b4d";

  /** A Data structure URN of another instance, as a package from that instance carries it. */
  private static final String FOREIGN_URN =
      "urn:core:platform:civitas:datastructure:common:" + FOREIGN_UUID;

  /** The UUID of an Element on another instance. */
  private static final String FOREIGN_ELEMENT_UUID = "5d1c7a2e-3b4f-4a6d-8e9f-0a1b2c3d4e5f";

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
    Map<String, Object> dataSource = new LinkedHashMap<>(member("Tree sensor", treeModel()));
    dataSource.put("kind", "datasource");

    ResponseEntity<String> refused =
        post(request(PACKAGE_ID, member("Tree cadastre", treeModel()), dataSource));

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

  private static Map<String, Object> member(String name, Map<String, Object> model) {
    return Map.of(
        "kind",
        "datastructure",
        "urn",
        FOREIGN_URN,
        "name",
        name,
        "description",
        "Installed by the integration test",
        "content",
        model);
  }

  @SafeVarargs
  private static Map<String, Object> request(String packageId, Map<String, Object>... members) {
    return Map.of(
        "package", Map.of("id", packageId, "version", "1.0.0", "members", List.of(members)));
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
