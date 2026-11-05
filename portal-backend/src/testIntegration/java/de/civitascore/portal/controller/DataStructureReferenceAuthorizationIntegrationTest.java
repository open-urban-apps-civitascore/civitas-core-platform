package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.JWTParser;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.RoleDefault;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.PermissionRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * References to other data structures' models, written through the version endpoints by a caller
 * who holds Data Architect on its own structure only.
 */
@DisplayName("Data structure model references")
class DataStructureReferenceAuthorizationIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String NS = "urn:core:platform:civitas:";
  private static final String REFUSED_TYPE = "urn:civitas:error:MODEL_REFERENCE_NOT_AVAILABLE";

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private ModelRegistryGateway gateway;
  @Autowired private DataStructureRepository dataStructureRepository;
  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private ObjectMapper mapper;
  @Autowired private DataSource dataSource;

  private String token;
  private Group group;

  private UUID bravo;
  private UUID bravoVersion;
  private String bravoMemberPin;

  @BeforeEach
  void storeAnotherTeamsDataStructure() throws Exception {
    token = getValidAccessToken();
    group = portalData.groupOf(JWTParser.parse(token).getJWTClaimsSet().getSubject());
    bravo = createStructure("Bravo");
    ResponseEntity<String> version =
        postVersion(
            bravo,
            library(
                "BravoRoot",
                Map.of(
                    "secretField", Map.of("type", "string"),
                    "inner", Map.of("$ref", "#/$defs/BravoInner")),
                Map.of(
                    "BravoInner",
                    Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of("innerSecret", Map.of("type", "string"))))),
            "*");
    assertThat(version.getStatusCode().value()).isEqualTo(201);
    bravoVersion = UUID.fromString(json(version).get("id").asString());
    JsonNode stored =
        mapper.valueToTree(
            gateway.fetchStoredModel(NS + "datastructure:common:" + bravo).orElseThrow().content());
    bravoMemberPin = stored.path("$defs").path("BravoInner").path("$ref").asString();
  }

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  private HttpHeaders headers(String scope) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Allowed-Scope-Ids", scope);
    return headers;
  }

  private ResponseEntity<String> call(HttpMethod method, String path, Object body, String scope) {
    return restTemplate.exchange(
        path, method, new HttpEntity<>(body, headers(scope)), String.class);
  }

  private JsonNode json(ResponseEntity<String> response) {
    return mapper.readTree(response.getBody());
  }

  private UUID createStructure(String name) {
    ResponseEntity<String> response =
        call(
            HttpMethod.POST,
            "/datastructures",
            Map.of(
                "name",
                name + "-" + UUID.randomUUID().toString().substring(0, 6),
                "description",
                "reference test"),
            "*");
    return UUID.fromString(json(response).get("id").asString());
  }

  private ResponseEntity<String> postVersion(UUID structure, Object model, String scope) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("description", "reference test");
    body.put("model", model);
    return call(HttpMethod.POST, "/datastructures/" + structure + "/versions", body, scope);
  }

  /** A model in the shape the data structure editor exports. */
  private static Map<String, Object> library(
      String root, Map<String, Object> rootProperties, Map<String, Object> extraDefs) {
    Map<String, Object> defs = new LinkedHashMap<>();
    defs.put(root, Map.of("type", "object", "title", root, "properties", rootProperties));
    defs.putAll(extraDefs);
    Map<String, Object> model = new LinkedHashMap<>();
    model.put("title", root);
    model.put("$ref", "#/$defs/" + root);
    model.put("$defs", defs);
    return model;
  }

  private static Map<String, Object> referencing(Object reference) {
    return library("CharlieRoot", Map.of("x", reference), Map.of());
  }

  /** A structure the caller holds Data Architect on, and nothing else. */
  private UUID charlie() {
    UUID charlie = createStructure("Charlie");
    portalData.assignment(
        group,
        portalData.role(RoleDefault.DATA_ARCHITECT),
        dataStructureRepository.findById(charlie).orElseThrow());
    return charlie;
  }

  private void grantRead(UUID structure) {
    portalData.assignment(
        group,
        portalData.role(RoleDefault.DATA_GATEKEEPER),
        dataStructureRepository.findById(structure).orElseThrow());
  }

  private ResponseEntity<String> attempt(Object model) {
    UUID charlie = charlie();
    return postVersion(charlie, model, charlie.toString());
  }

  private static void assertRefused(ResponseEntity<String> response, ObjectMapper mapper) {
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(mapper.readTree(response.getBody()).get("type").asString()).isEqualTo(REFUSED_TYPE);
  }

  private void assertBravoDeletable() {
    assertThat(
            call(
                    HttpMethod.GET,
                    "/datastructures/" + bravo + "/versions/" + bravoVersion,
                    null,
                    "*")
                .getBody())
        .contains("\"inUse\":false");
    assertThat(
            call(HttpMethod.DELETE, "/datastructures/" + bravo, null, "*").getStatusCode().value())
        .isEqualTo(204);
  }

  @Nested
  @DisplayName("A caller who may not read the other data structure")
  class Unreadable {

    @Test
    @DisplayName("cannot name its member as a property type")
    void propertyReferenceIsRefused() {
      assertRefused(attempt(referencing(Map.of("$ref", bravoMemberPin))), mapper);
      assertBravoDeletable();
    }

    @Test
    @DisplayName("cannot list its member as a member of its own")
    void bareMemberIsRefused() {
      assertRefused(
          attempt(
              library(
                  "CharlieRoot",
                  Map.of("name", Map.of("type", "string")),
                  Map.of("Stolen", Map.of("$ref", bravoMemberPin)))),
          mapper);
      assertBravoDeletable();
    }

    @Test
    @DisplayName("cannot embed the data structure itself")
    void dataStructureReferenceIsRefused() {
      assertRefused(
          attempt(referencing(Map.of("$ref", NS + "datastructure:common:" + bravo + ":1.0.0"))),
          mapper);
    }

    @Test
    @DisplayName("cannot link to its member with x-core-ref")
    void linkIsRefused() {
      assertRefused(
          attempt(
              referencing(
                  Map.of(
                      "type",
                      "string",
                      "x-core-ref",
                      Map.of("type", gateway.logicalUrn(bravoMemberPin))))),
          mapper);
      assertBravoDeletable();
    }

    @Test
    @DisplayName("gets the same answer for a model that does not exist")
    void unknownAndUnreadableReadTheSame() {
      String missing = NS + "element:common:" + UUID.randomUUID() + ":1.0.0";
      JsonNode unreadable = json(attempt(referencing(Map.of("$ref", bravoMemberPin))));
      JsonNode unknownRef = json(attempt(referencing(Map.of("$ref", missing))));
      JsonNode unknownLink =
          json(
              attempt(
                  referencing(
                      Map.of(
                          "type",
                          "string",
                          "x-core-ref",
                          Map.of("type", gateway.logicalUrn(missing))))));

      for (JsonNode refusal : List.of(unknownRef, unknownLink)) {
        assertThat(refusal.get("type")).isEqualTo(unreadable.get("type"));
        assertThat(refusal.get("detail")).isEqualTo(unreadable.get("detail"));
      }
      assertThat(unreadable.get("detail").asString()).doesNotContain(bravo.toString());
    }

    @Test
    @DisplayName("cannot reference it from a structure first stored with a shape of its own")
    void elementHostedStructureIsRefused() {
      UUID charlie = charlie();
      ResponseEntity<String> shaped =
          postVersion(
              charlie,
              Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))),
              charlie.toString());
      assertThat(shaped.getStatusCode().value()).isEqualTo(201);

      assertRefused(
          postVersion(
              charlie,
              Map.of("$ref", NS + "datastructure:common:" + bravo + ":1.0.0"),
              charlie.toString()),
          mapper);
    }

    @Test
    @DisplayName("is refused even with a tenant-wide UPDATE grant and the wildcard header")
    void wildcardWithoutReadIsRefused() {
      UUID charlie = createStructure("Charlie");
      portalData.assignment(
          group,
          portalData.role(
              rb ->
                  rb.permissions(
                      new HashSet<>(
                          Set.of(
                              permissionRepository
                                  .findByName(PermissionName.DATASTRUCTURE_UPDATE.name())
                                  .orElseThrow())))));

      assertRefused(postVersion(charlie, referencing(Map.of("$ref", bravoMemberPin)), "*"), mapper);
    }
  }

  @Nested
  @DisplayName("A caller who may read the other data structure")
  class Readable {

    @Test
    @DisplayName("may embed its member with a version")
    void pinnedReferenceIsAccepted() {
      grantRead(bravo);

      assertThat(attempt(referencing(Map.of("$ref", bravoMemberPin))).getStatusCode().value())
          .isEqualTo(201);
    }

    @Test
    @DisplayName("may not embed its member without a version")
    void unpinnedReferenceIsRefused() {
      grantRead(bravo);

      assertRefused(
          attempt(referencing(Map.of("$ref", gateway.logicalUrn(bravoMemberPin)))), mapper);
    }

    @Test
    @DisplayName("may link to its member without a version")
    void unpinnedLinkIsAccepted() {
      grantRead(bravo);

      assertThat(
              attempt(
                      referencing(
                          Map.of(
                              "type",
                              "string",
                              "x-core-ref",
                              Map.of("type", gateway.logicalUrn(bravoMemberPin)))))
                  .getStatusCode()
                  .value())
          .isEqualTo(201);
    }

    @Test
    @DisplayName("may do so with a tenant-wide READ grant")
    void tenantWideReadIsAccepted() {
      UUID charlie = createStructure("Charlie");
      portalData.assignment(group, portalData.role(RoleDefault.DATA_ARCHITECT));

      assertThat(
              postVersion(charlie, referencing(Map.of("$ref", bravoMemberPin)), "*")
                  .getStatusCode()
                  .value())
          .isEqualTo(201);
    }
  }

  @Nested
  @DisplayName("References that stay allowed")
  class Allowed {

    @Test
    @DisplayName("a published structure's member, with a version")
    void publishedStructureIsAccepted() {
      JsonNode published =
          json(call(HttpMethod.GET, "/published-structures", null, "*"))
              .path(0)
              .path("structures")
              .path(0);
      JsonNode stored =
          mapper.valueToTree(
              gateway.fetchStoredModel(published.path("urn").asString()).orElseThrow().content());
      String member =
          stored.path("$defs").properties().iterator().next().getValue().path("$ref").asString();

      assertThat(attempt(referencing(Map.of("$ref", member))).getStatusCode().value())
          .isEqualTo(201);
      assertRefused(attempt(referencing(Map.of("$ref", gateway.logicalUrn(member)))), mapper);
    }

    @Test
    @DisplayName("GeoJSON and the structure's own members, also of an earlier version")
    void ownAndGeoJsonReferencesAreAccepted() {
      UUID charlie = charlie();
      ResponseEntity<String> first =
          postVersion(
              charlie,
              library(
                  "CharlieRoot",
                  Map.of(
                      "geo", Map.of("$ref", "https://geojson.org/schema/Point.json"),
                      "shared", Map.of("$ref", "#/$defs/Shared")),
                  Map.of("Shared", Map.of("type", "object"))),
              charlie.toString());
      assertThat(first.getStatusCode().value()).isEqualTo(201);

      JsonNode stored =
          mapper.valueToTree(
              gateway
                  .fetchStoredModel(NS + "datastructure:common:" + charlie)
                  .orElseThrow()
                  .content());
      String shared =
          gateway.logicalUrn(stored.path("$defs").path("Shared").path("$ref").asString());
      ResponseEntity<String> second =
          postVersion(
              charlie,
              library("CharlieNext", Map.of("shared", Map.of("$ref", shared)), Map.of()),
              charlie.toString());

      assertThat(second.getStatusCode().value()).isEqualTo(201);
      ResponseEntity<String> patch =
          call(
              HttpMethod.PATCH,
              "/datastructures/" + charlie + "/versions/" + json(first).get("id").asString(),
              Map.of("model", Map.of("title", "Renamed")),
              charlie.toString());
      assertThat(patch.getStatusCode().value()).isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("A reference stored before the check")
  class StoredBefore {

    private UUID charlie;
    private DataStructureVersion version;

    @BeforeEach
    void storeAForeignReferenceDirectly() {
      // Stored through the factory, which writes the registry directly, as no check did before.
      DataStructure structure = portalData.dataStructure();
      charlie = structure.getId();
      portalData.assignment(group, portalData.role(RoleDefault.DATA_ARCHITECT), structure);
      version =
          portalData.attachModel(
              portalData.dataStructureVersion(structure),
              library("CharlieRoot", Map.of("x", Map.of("$ref", bravoMemberPin)), Map.of()));
    }

    @Test
    @DisplayName("does not stop editing the version")
    void editingStillWorks() {
      ResponseEntity<String> patch =
          call(
              HttpMethod.PATCH,
              "/datastructures/" + charlie + "/versions/" + version.getId(),
              Map.of("description", "edited", "styles", Map.of("color", "blue")),
              charlie.toString());

      assertThat(patch.getStatusCode().value()).isEqualTo(200);
    }

    private ResponseEntity<String> save(Map<String, Object> rootProperties) {
      return call(
          HttpMethod.PUT,
          "/datastructures/" + charlie + "/versions/" + version.getId(),
          Map.of(
              "description", "edited", "model", library("CharlieRoot", rootProperties, Map.of())),
          charlie.toString());
    }

    @Test
    @DisplayName("does not stop a save of the full model that keeps it")
    void savingTheFullModelThatKeepsItWorks() {
      ResponseEntity<String> put =
          save(Map.of("x", Map.of("$ref", bravoMemberPin), "y", Map.of("type", "string")));

      assertThat(put.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("does not let a save add another reference the caller may not read")
    void savingAnotherUnreadableReferenceIsRefused() {
      assertRefused(
          save(
              Map.of(
                  "x",
                  Map.of("$ref", bravoMemberPin),
                  "z",
                  Map.of("$ref", NS + "datastructure:common:" + bravo + ":1.0.0"))),
          mapper);
    }

    @Test
    @DisplayName("stops the release by a caller who may not read the target")
    void releaseIsRefusedForANonReader() {
      assertRefused(
          call(
              HttpMethod.POST,
              "/datastructures/" + charlie + "/versions/" + version.getId() + "/release",
              null,
              charlie.toString()),
          mapper);
      assertThat(
              dataStructureVersionRepository
                  .findById(version.getId())
                  .orElseThrow()
                  .getDataStructureVersionStatus())
          .hasToString("DRAFT");
    }

    @Test
    @DisplayName("stops the duplicate by a caller who may not read the target, leaving no copy")
    void duplicateIsRefusedForANonReader() {
      assertRefused(
          call(
              HttpMethod.POST,
              "/datastructures/" + charlie + "/versions/" + version.getId() + "/duplicate",
              null,
              charlie.toString()),
          mapper);
      assertThat(dataStructureVersionRepository.findAllByDataStructure_IdIn(List.of(charlie)))
          .hasSize(1);
      assertThat(
              JdbcClient.create(dataSource)
                  .sql("SELECT current_version FROM model_forge.artifact WHERE logical_urn = :urn")
                  .param("urn", UrnParser.logicalUrn(version.getModelUrn()))
                  .query(String.class)
                  .single())
          .isEqualTo(UrnParser.versionFromUrn(version.getModelUrn()));
    }

    @Test
    @DisplayName("is released by a caller who may read the target")
    void releaseIsAcceptedForAReader() {
      grantRead(bravo);

      ResponseEntity<String> release =
          call(
              HttpMethod.POST,
              "/datastructures/" + charlie + "/versions/" + version.getId() + "/release",
              null,
              charlie.toString());

      assertThat(release.getStatusCode().value()).isEqualTo(200);
    }
  }
}
