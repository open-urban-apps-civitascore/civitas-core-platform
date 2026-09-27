package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureSummaryDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("DataStructureVersion Controller Integration Tests")
class DataStructureVersionControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired protected PortalTestDataFactory portalData;

  @Autowired private DataStructureRepository dataStructureRepository;

  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;

  @Autowired private DataSourceRepository dataSourceRepository;

  private UUID dataStructureId;
  private UUID versionId1;

  @BeforeEach
  void initTestData() {
    // Create parent data structure
    DataStructure dataStructure =
        portalData.dataStructure(
            b ->
                b.name("Test Data Structure")
                    .description("Data structure for version testing")
                    .dataStructureStatus(DataStructureStatus.DRAFT));
    dataStructureId = dataStructure.getId();

    // Create test versions (models/styles live in the Model Forge registry, attached after save)
    DataStructureVersion version1 =
        portalData.dataStructureVersion(
            dataStructure,
            b ->
                b.description("First version of the test data structure")
                    .dataStructureVersionStatus(DataStructureVersionStatus.DRAFT)
                    .modelName("TestModel1"));
    version1 =
        portalData.attachModel(
            version1,
            portalData.dataStructureVersionModel("Model1"),
            Map.of("color", "blue", "size", 10));
    versionId1 = version1.getId();

    DataStructureVersion version2 =
        portalData.dataStructureVersion(
            dataStructure,
            b ->
                b.description("Second version with updated fields")
                    .dataStructureVersionStatus(DataStructureVersionStatus.DRAFT)
                    .modelName("TestModel2"));
    portalData.attachModel(
        version2,
        portalData.dataStructureVersionModel("Model2"),
        Map.of("color", "red", "size", 20));
  }

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  private HttpHeaders createAuthHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(MediaType.parseMediaTypes("application/json"));
    return headers;
  }

  private String getEndpoint() {
    return "/datastructures/" + dataStructureId + "/versions";
  }

  protected ParameterizedTypeReference<DataStructureVersionOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Nested
  @DisplayName("Create DataStructureVersion Tests")
  class CreateDataStructureVersionTests {

    @Test
    @DisplayName("A version whose content already exists is refused")
    void shouldRefuseAVersionWithContentThatAlreadyExists() {
      // The registry returns the version already stored for a byte-identical write, so a second
      // version cannot be given a number of its own. Refusing says so instead of yielding a
      // version that shares another's number while claiming to be a new one.
      DataStructureVersionInputDTO first = new DataStructureVersionInputDTO();
      first.setDescription("first");
      first.setModelName("SharedShape");
      first.setModel(portalData.dataStructureVersionModel("SharedShape"));

      ResponseEntity<DataStructureVersionOutputDTO> firstResponse =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(first, createAuthHeaders()),
              getOutputTypeReference());
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      DataStructureVersionInputDTO second = new DataStructureVersionInputDTO();
      second.setDescription("second, same content");
      second.setModelName("SharedShape");
      second.setModel(portalData.dataStructureVersionModel("SharedShape"));

      ResponseEntity<ProblemDetail> secondResponse =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(second, createAuthHeaders()),
              new ParameterizedTypeReference<ProblemDetail>() {});

      assertThat(secondResponse.getStatusCode())
          .as("The number is already taken by the version holding this content")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should create data structure version successfully with valid data")
    void shouldCreateDataStructureVersionSuccessfully() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDescription("Third version with new features");
      input.setModel(portalData.dataStructureVersionModel("Model3"));
      input.setModelName("TestModel3");

      Map<String, Object> styles = new HashMap<>();
      styles.put("color", "green");
      styles.put("size", 15);
      input.setStyles(styles);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getVersion())
          .as("A new version is a new contract, so its model starts the next major line")
          .isEqualTo("3.0.0");
      assertThat(output.getDescription())
          .as("Description should match input")
          .isEqualTo("Third version with new features");
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should be set to DRAFT by preProcessCreateInput hook")
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      assertThat(output.getDataStructureVersionSource())
          .as("Source is set by the backend, the input does not carry it")
          .isEqualTo(DataStructureVersionSource.OWN);
      assertThat(output.getModel())
          .as("Model (JSON Schema) should be persisted and returned")
          .containsEntry("title", "Model3");
      assertThat(output.getModelName()).as("Model name should match input").isEqualTo("TestModel3");
      assertThat(output.getStyles().get("color"))
          .as("Styles color should match input")
          .isEqualTo("green");
      assertThat(output.getDataStructure().getId())
          .as("DataStructure ID should be set from path variable")
          .isEqualTo(dataStructureId);
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();

      assertThat(response.getHeaders().getLocation())
          .as("Location header should be present")
          .isNotNull();
    }

    @Test
    @DisplayName("Should create a DRAFT version without a model")
    void shouldCreateVersionWithoutModel() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setModelName("NoModelYet");

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModel())
          .as("Model is optional in DRAFT and may be null")
          .isNull();
    }

    @Test
    @DisplayName("Should reject a model that is not a conforming JSON Schema")
    void shouldRejectNonConformingModel() {
      // portal-backend does not validate the model itself; the registry refuses it on the way in.
      // Without this, a break anywhere in that chain would persist an invalid model behind a 2xx.
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setModelName("NonConforming");
      input.setModel(Map.of("type", "object", "properties", Map.of("t", Map.of("type", "nubmer"))));

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody())
          .as("the rejection names the position the author has to fix")
          .contains("/properties/t/type");
    }

    @Test
    @DisplayName("Should fail to create version without authentication")
    void shouldFailToCreateVersionWithoutAuth() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(), HttpMethod.POST, new HttpEntity<>(input), String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should set dataStructureId from path variable, not from payload")
    void shouldSetDataStructureIdFromPathVariable() {
      // Create another data structure
      DataStructure otherDataStructure = new DataStructure();
      otherDataStructure.setName("Other Data Structure");
      otherDataStructure.setDescription("Should not be used");
      otherDataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      otherDataStructure = dataStructureRepository.save(otherDataStructure);
      UUID otherDataStructureId = otherDataStructure.getId();

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setModel(portalData.dataStructureVersionModel("Model5"));
      // Try to set a different dataStructureId - should be ignored due to @JsonIgnore
      input.setDataStructureId(otherDataStructureId);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getDataStructure()).isNotNull();
      assertThat(output.getDataStructure().getId())
          .as("DataStructure ID should be from path variable, not payload")
          .isEqualTo(dataStructureId)
          .isNotEqualTo(otherDataStructureId);
    }

    // Duplicate-version conflicts no longer exist: the version string is not a client input —
    // Model Forge is the sole version authority and always assigns the next free version.
  }

  @Nested
  @DisplayName("Read DataStructureVersion Tests")
  class ReadDataStructureVersionTests {

    @Test
    @DisplayName("Should retrieve data structure version by ID successfully")
    void shouldRetrieveDataStructureVersionById() {
      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(versionId1);
      assertThat(output.getVersion()).isEqualTo("1.0.0");
      assertThat(output.getDescription()).isEqualTo("First version of the test data structure");
      assertThat(output.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      assertThat(output.getDataStructureVersionSource()).isEqualTo(DataStructureVersionSource.OWN);
      assertThat(output.getModelName()).isEqualTo("TestModel1");
      assertThat(output.getStyles().get("color")).isEqualTo("blue");
      assertThat(output.getModel())
          .as("Persisted model (JSON Schema) should round-trip from the DB")
          .containsEntry("title", "Model1");

      DataStructureSummaryDTO dataStructureSummary = output.getDataStructure();
      assertThat(dataStructureSummary).isNotNull();
      assertThat(dataStructureSummary.getId()).isEqualTo(dataStructureId);
      assertThat(dataStructureSummary.getName()).isEqualTo("Test Data Structure");
    }

    @Test
    @DisplayName("Should return model as null when none was persisted")
    void shouldReturnNullModelWhenNoneSet() {
      DataStructureVersion versionWithoutModel = new DataStructureVersion();
      versionWithoutModel.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      versionWithoutModel.setVersion("3.0.0");
      versionWithoutModel.setDataStructure(
          dataStructureRepository.findById(dataStructureId).orElseThrow());
      versionWithoutModel = dataStructureVersionRepository.save(versionWithoutModel);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionWithoutModel.getId(),
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModel())
          .as("Model should be null when none was persisted")
          .isNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent version")
    void shouldReturn404ForNonExistentVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID(),
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 405 for getAll (endpoint disabled)")
    void shouldRejectGetAll() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(), HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);

      assertThat(response.getStatusCode())
          .as("Should return METHOD_NOT_ALLOWED status")
          .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
  }

  @Nested
  @DisplayName("Update DataStructureVersion Tests")
  class UpdateDataStructureVersionTests {

    @Test
    @DisplayName("Should update data structure version successfully")
    void shouldUpdateDataStructureVersionSuccessfully() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDescription("Updated description with a replaced model");
      input.setModel(portalData.dataStructureVersionModel("Model1Updated"));
      input.setModelName("TestModel1-Updated");

      Map<String, Object> styles = new HashMap<>();
      styles.put("color", "purple");
      styles.put("size", 25);
      input.setStyles(styles);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(versionId1);
      assertThat(output.getVersion())
          .as("Editing a version advances the minor inside its own major, not from the newest")
          .isEqualTo("1.1.0");
      assertThat(output.getDescription()).isEqualTo("Updated description with a replaced model");
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should remain DRAFT (not changed by update)")
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      assertThat(output.getModel())
          .as("Model should be replaced by the update")
          .containsEntry("title", "Model1Updated");
      assertThat(output.getModelName()).isEqualTo("TestModel1-Updated");
      assertThat(output.getStyles().get("color")).isEqualTo("purple");
      assertThat(output.getStyles().get("size")).isEqualTo(25);
    }

    @Test
    @DisplayName("Should return 404 when updating non-existent version")
    void shouldReturn404WhenUpdatingNonExistent() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID(),
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should maintain dataStructureId on update")
    void shouldMaintainDataStructureIdOnUpdate() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getDataStructure()).isNotNull();
      assertThat(output.getDataStructure().getId())
          .as("DataStructure ID should remain unchanged")
          .isEqualTo(dataStructureId);
    }

    @Test
    @DisplayName("Update without a model keeps the version's stored pin unchanged")
    void shouldKeepPinOnUpdateWithoutModel() {
      // The version string is assigned by Model Forge; an update that carries no model stores
      // nothing in the registry, so the existing pin (version + URN) must survive the update.
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDescription("Metadata-only update");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      DataStructureVersion unchanged =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(unchanged.getVersion())
          .as("Version string of versionId1 must not have changed")
          .isEqualTo("1.0.0");
      assertThat(unchanged.getModelUrn())
          .as("The registry pin must survive a model-less update")
          .isNotNull();
    }
  }

  @Nested
  @DisplayName("Patch DataStructureVersion Tests")
  class PatchDataStructureVersionTests {

    @Test
    @DisplayName("Should patch description without affecting the model")
    void shouldPatchDescriptionOnly() {
      Map<String, Object> patchMap =
          Collections.singletonMap("description", "Patched version description");

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PATCH,
              new HttpEntity<>(patchMap, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("PATCH with only description should return OK")
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getDescription()).isEqualTo("Patched version description");
      assertThat(output.getVersion()).as("Version should remain unchanged").isEqualTo("1.0.0");
      assertThat(output.getModel())
          .as("Model should remain unchanged by a description-only patch")
          .containsEntry("title", "Model1");
    }

    @Test
    @DisplayName("Should patch the model")
    void shouldPatchModel() {
      Map<String, Object> patchMap =
          Collections.singletonMap("model", portalData.dataStructureVersionModel("Model1Patched"));

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PATCH,
              new HttpEntity<>(patchMap, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("PATCH with model should return OK")
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModel()).containsEntry("title", "Model1Patched");
    }

    @Test
    @DisplayName("Should return 404 when patching non-existent version")
    void shouldReturn404WhenPatchingNonExistent() {
      Map<String, Object> patchMap = Collections.singletonMap("description", "Patched description");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID(),
              HttpMethod.PATCH,
              new HttpEntity<>(patchMap, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Delete DataStructureVersion Tests")
  class DeleteDataStructureVersionTests {

    @Test
    @DisplayName("Should delete data structure version successfully")
    void shouldDeleteDataStructureVersionSuccessfully() {
      ResponseEntity<Void> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              Void.class);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<String> getResponse =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(getResponse.getStatusCode())
          .as("Should return NOT_FOUND after deletion")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete last released version of a released data structure")
    void shouldFailToDeleteLastReleasedVersionOfReleasedDataStructure() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructure dataStructure = dataStructureRepository.findById(dataStructureId).orElseThrow();
      dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dataStructureRepository.save(dataStructure);

      // Try to delete the only released version - should fail
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      // Verify dataStructure and version still exist
      assertThat(dataStructureRepository.existsById(dataStructureId)).isTrue();
      assertThat(dataStructureVersionRepository.existsById(versionId1)).isTrue();
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent version")
    void shouldReturn404WhenDeletingNonExistent() {
      ResponseEntity<Void> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID(),
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              Void.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Release DataStructureVersion Tests")
  class ReleaseDataStructureVersionTests {

    @Test
    @DisplayName("Should release version with a model successfully")
    void shouldReleaseVersionWithModel() {
      DataStructureVersion version =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(version.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      // The model lives in the registry; the shell mirrors its pin.
      assertThat(version.getModelUrn()).isNotNull();

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/release",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(versionId1);
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should be AVAILABLE after releasing")
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
      assertThat(output.getVersion()).isEqualTo("1.0.0");

      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to release version without a model")
    void shouldFailToReleaseVersionWithoutModel() {
      DataStructureVersion versionWithoutModel = new DataStructureVersion();
      versionWithoutModel.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      versionWithoutModel.setVersion("3.0.0");
      versionWithoutModel.setModelName("TestModel3");
      versionWithoutModel.setDataStructure(
          dataStructureRepository.findById(dataStructureId).orElseThrow());
      versionWithoutModel = dataStructureVersionRepository.save(versionWithoutModel);
      UUID versionWithoutModelId = versionWithoutModel.getId();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionWithoutModelId + "/release",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionWithoutModelId).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .as("Status should remain DRAFT")
          .isEqualTo(DataStructureVersionStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to release already released version")
    void shouldFailToReleaseAlreadyReleasedVersion() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/release",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when releasing non-existent version")
    void shouldReturn404WhenReleasingNonExistentVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID() + "/release",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to release without authentication")
    void shouldFailToReleaseWithoutAuth() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/release",
              HttpMethod.POST,
              new HttpEntity<>((HttpHeaders) null),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Unrelease DataStructureVersion Tests")
  class UnreleaseDataStructureVersionTests {

    @Test
    @DisplayName("Should unrelease released version successfully")
    void shouldUnreleaseReleasedVersionSuccessfully() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unrelease",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(versionId1);
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should be DRAFT after unreleasing")
          .isEqualTo(DataStructureVersionStatus.DRAFT);

      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to unrelease already draft version")
    void shouldFailToUnreleaseDraftVersion() {
      DataStructureVersion version =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(version.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unrelease",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when unreleasing non-existent version")
    void shouldReturn404WhenUnreleasingNonExistentVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID() + "/unrelease",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to unrelease without authentication")
    void shouldFailToUnreleaseWithoutAuth() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unrelease",
              HttpMethod.POST,
              new HttpEntity<>((HttpHeaders) null),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to unrelease the only released version of a released DataStructure")
    void shouldFailToUnreleaseOnlyReleasedVersionOfReleasedDataStructure() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructure dataStructure = dataStructureRepository.findById(dataStructureId).orElseThrow();
      dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dataStructureRepository.save(dataStructure);

      // Try to unrelease the only released version - should fail
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unrelease",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      // Verify version is still released
      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .as("Version should remain released")
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName(
        "Should allow unreleasing when DataStructure has multiple released versions and is released")
    void shouldAllowUnreleasingWhenMultipleReleasedVersionsExist() {
      DataStructureVersion version3 = new DataStructureVersion();
      version3.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version3.setModelName("TestModel3");
      version3.setDataStructure(dataStructureRepository.findById(dataStructureId).orElseThrow());
      version3 = dataStructureVersionRepository.save(version3);
      version3 = portalData.attachModel(version3, portalData.dataStructureVersionModel("Model3"));
      UUID version3Id = version3.getId();

      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      version3.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version3);

      DataStructure dataStructure = dataStructureRepository.findById(dataStructureId).orElseThrow();
      dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dataStructureRepository.save(dataStructure);

      // Now unrelease one version - should succeed because there's another released version
      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unrelease",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      DataStructureVersion reloadedVersion1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion1.getDataStructureVersionStatus())
          .as("Version 1 should be unreleased")
          .isEqualTo(DataStructureVersionStatus.DRAFT);

      DataStructureVersion reloadedVersion3 =
          dataStructureVersionRepository.findById(version3Id).orElseThrow();
      assertThat(reloadedVersion3.getDataStructureVersionStatus())
          .as("Version 3 should still be released")
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);

      DataStructure reloadedDataStructure =
          dataStructureRepository.findById(dataStructureId).orElseThrow();
      assertThat(reloadedDataStructure.getDataStructureStatus())
          .as("DataStructure should remain released")
          .isEqualTo(DataStructureStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should allow unreleasing when DataStructure is in DRAFT status")
    void shouldAllowUnreleasingWhenDataStructureIsDraft() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructure dataStructure = dataStructureRepository.findById(dataStructureId).orElseThrow();
      assertThat(dataStructure.getDataStructureStatus()).isEqualTo(DataStructureStatus.DRAFT);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unrelease",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .as("Version should be unreleased")
          .isEqualTo(DataStructureVersionStatus.DRAFT);
    }
  }

  @Nested
  @DisplayName("Update Released Meta Tests")
  class UpdateReleasedMetaTests {

    private UUID releasedVersionId;

    @BeforeEach
    void releaseVersion() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      releasedVersionId = versionId1;
    }

    @Test
    @DisplayName("Should change only modelName via released/meta when not in use")
    void shouldChangeOnlyModelNameWhenNotInUse() {
      String path = getEndpoint() + "/" + releasedVersionId;
      DataStructureVersionOutputDTO before =
          restTemplate
              .exchange(
                  path,
                  HttpMethod.GET,
                  new HttpEntity<>(createAuthHeaders()),
                  getOutputTypeReference())
              .getBody();
      assertThat(before).isNotNull();

      Map<String, Object> patch = new HashMap<>();
      patch.put("modelName", "UpdatedReleasedModel");
      patch.put("model", portalData.dataStructureVersionModel("ReleasedModel"));
      patch.put("styles", Map.of("color", "red"));

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              path + "/released/meta",
              HttpMethod.PATCH,
              new HttpEntity<>(patch, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getModelName()).isEqualTo("UpdatedReleasedModel");
      assertThat(output.getDescription()).isEqualTo(before.getDescription());
      assertThat(output.getModel()).isEqualTo(before.getModel());
      assertThat(output.getStyles()).isEqualTo(before.getStyles());
      assertThat(output.getVersion()).isEqualTo(before.getVersion());
      assertThat(output.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to update released meta for DRAFT version")
    void shouldFailToUpdateReleasedMetaForDraftVersion() {
      DataStructureVersion draftVersion = new DataStructureVersion();
      draftVersion.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      draftVersion.setModelName("TestModel4");
      draftVersion.setDataStructure(
          dataStructureRepository.findById(dataStructureId).orElseThrow());
      draftVersion = dataStructureVersionRepository.save(draftVersion);
      draftVersion =
          portalData.attachModel(draftVersion, portalData.dataStructureVersionModel("Model4"));
      UUID draftVersionId = draftVersion.getId();

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + draftVersionId + "/released/meta",
              HttpMethod.PATCH,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when updating released meta for non-existent version")
    void shouldReturn404WhenUpdatingReleasedMetaForNonExistentVersion() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID() + "/released/meta",
              HttpMethod.PATCH,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update released meta without authentication")
    void shouldFailToUpdateReleasedMetaWithoutAuth() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + releasedVersionId + "/released/meta",
              HttpMethod.PATCH,
              new HttpEntity<>(input),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Regular Update Restrictions Tests")
  class RegularUpdateRestrictionsTests {

    @Test
    @DisplayName("Should fail to update released version with regular PUT endpoint")
    void shouldFailToUpdateReleasedVersionWithRegularPut() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("InUse Guard Tests")
  class InUseGuardTests {

    private UUID inUseDataStructureId;
    private UUID inUseVersionId;

    @BeforeEach
    void setupInUseVersion() {
      cleanup();

      DataStructure ds = new DataStructure();
      ds.setName("InUse Data Structure");
      ds.setDescription("Data structure with in-use version");
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      ds = dataStructureRepository.save(ds);
      inUseDataStructureId = ds.getId();

      DataStructureVersion version = new DataStructureVersion();
      version.setDataStructure(ds);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setModelName("InUse Model");
      version = dataStructureVersionRepository.save(version);
      version = portalData.attachModel(version, portalData.dataStructureVersionModel("InUse"));
      inUseVersionId = version.getId();

      pinDataSource(version, DataSourceStatus.AVAILABLE);
    }

    private void pinDataSource(DataStructureVersion version, DataSourceStatus status) {
      DataSource dataSource = new DataSource();
      dataSource.setName("ds_referencing_" + UUID.randomUUID().toString().substring(0, 8));
      dataSource.setDataSourceStatus(status);
      dataSource.setDataStructureVersion(version);
      dataSourceRepository.save(dataSource);
    }

    @Test
    @DisplayName("Should report inUseByReleased=false when only a draft pins")
    void shouldReportNotInUseByReleasedWhenOnlyADraftPins() {
      DataStructureVersion draftPinned = new DataStructureVersion();
      draftPinned.setDataStructure(
          dataStructureRepository.findById(inUseDataStructureId).orElseThrow());
      draftPinned.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      draftPinned.setModelName("Draft pinned");
      draftPinned = dataStructureVersionRepository.save(draftPinned);
      draftPinned =
          portalData.attachModel(draftPinned, portalData.dataStructureVersionModel("Before"));
      pinDataSource(draftPinned, DataSourceStatus.DRAFT);
      String path = "/datastructures/" + inUseDataStructureId + "/versions/" + draftPinned.getId();

      ResponseEntity<DataStructureVersionOutputDTO> read =
          restTemplate.exchange(
              path,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());
      assertThat(read.getBody()).isNotNull();
      assertThat(read.getBody().isInUse()).isTrue();
      assertThat(read.getBody().isInUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("Should return 409 when unreleasing in-use version")
    void shouldReturn409WhenUnreleasingInUseVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              "/datastructures/"
                  + inUseDataStructureId
                  + "/versions"
                  + "/"
                  + inUseVersionId
                  + "/unrelease",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return CONFLICT status")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should return 409 when deleting in-use version")
    void shouldReturn409WhenDeletingInUseVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              "/datastructures/" + inUseDataStructureId + "/versions" + "/" + inUseVersionId,
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return CONFLICT status")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should return inUse=true in version output DTO")
    void shouldReturnInUseTrueInVersionOutputDTO() {
      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              "/datastructures/" + inUseDataStructureId + "/versions" + "/" + inUseVersionId,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isInUse())
          .as("inUse should be true when a DataSource references this version")
          .isTrue();
      assertThat(response.getBody().isInUseByReleased())
          .as("inUseByReleased should be true when an AVAILABLE DataSource pins this version")
          .isTrue();
    }

    @Test
    @DisplayName("Should protect model and version via released/meta when version is in use")
    void shouldProtectStructuralFieldsWhenInUse() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setModel(portalData.dataStructureVersionModel("SHOULD_NOT_CHANGE"));
      input.setModelName("UpdatedModelName");

      input.setStyles(Collections.singletonMap("color", "green"));

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              "/datastructures/"
                  + inUseDataStructureId
                  + "/versions/"
                  + inUseVersionId
                  + "/released/meta",
              HttpMethod.PATCH,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getModel())
          .as("Model should be protected when in use")
          .containsEntry("title", "InUse");
      assertThat(output.getVersion())
          .as("Version should be protected when in use")
          .isEqualTo("1.0.0");
      assertThat(output.getStyles())
          .as("Styles should be protected when in use (the seeded version has none)")
          .isNull();
      assertThat(output.getModelName())
          .as("ModelName should be updatable even when in use")
          .isEqualTo("UpdatedModelName");
    }

    @Test
    @DisplayName("Should return inUse=false when no DataSource references version")
    void shouldReturnInUseFalseWhenNoDataSourceReferences() {
      DataStructureVersion notInUseVersion = new DataStructureVersion();
      notInUseVersion.setDataStructure(
          dataStructureRepository.findById(inUseDataStructureId).orElseThrow());
      notInUseVersion.setVersion("2.0.0");
      notInUseVersion.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      notInUseVersion = dataStructureVersionRepository.save(notInUseVersion);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              "/datastructures/"
                  + inUseDataStructureId
                  + "/versions"
                  + "/"
                  + notInUseVersion.getId(),
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isInUse())
          .as("inUse should be false when no DataSource references this version")
          .isFalse();
    }
  }

  @Nested
  @DisplayName("Parent ownership")
  class ParentOwnershipTests {

    private String pathUnderForeignDataStructure(String suffix) {
      UUID foreignDataStructureId = portalData.dataStructure().getId();
      return "/datastructures/" + foreignDataStructureId + "/versions/" + versionId1 + suffix;
    }

    private HttpStatus statusOf(String path, HttpMethod method, Object body) {
      return HttpStatus.valueOf(
          restTemplate
              .exchange(path, method, new HttpEntity<>(body, createAuthHeaders()), String.class)
              .getStatusCode()
              .value());
    }

    private void markVersionReleased() {
      DataStructureVersion version =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version);
    }

    private DataStructureVersionOutputDTO getUnderOwnDataStructure() {
      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      return response.getBody();
    }

    @Test
    @DisplayName("GET answers 404 for a version of another data structure")
    void getAnswers404ForForeignVersion() {
      assertThat(statusOf(pathUnderForeignDataStructure(""), HttpMethod.GET, null))
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PUT answers 404 and leaves the version under its data structure")
    void putAnswers404AndKeepsParentForForeignVersion() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDescription("moved");

      assertThat(statusOf(pathUnderForeignDataStructure(""), HttpMethod.PUT, input))
          .isEqualTo(HttpStatus.NOT_FOUND);

      DataStructureVersionOutputDTO output = getUnderOwnDataStructure();
      assertThat(output.getDataStructure().getId()).isEqualTo(dataStructureId);
      assertThat(output.getDescription()).isEqualTo("First version of the test data structure");
    }

    @Test
    @DisplayName("PATCH answers 404 and leaves the version under its data structure")
    void patchAnswers404AndKeepsParentForForeignVersion() {
      assertThat(
              statusOf(
                  pathUnderForeignDataStructure(""),
                  HttpMethod.PATCH,
                  Map.of("description", "moved")))
          .isEqualTo(HttpStatus.NOT_FOUND);

      DataStructureVersionOutputDTO output = getUnderOwnDataStructure();
      assertThat(output.getDataStructure().getId()).isEqualTo(dataStructureId);
      assertThat(output.getDescription()).isEqualTo("First version of the test data structure");
    }

    @Test
    @DisplayName("DELETE answers 404 and leaves the version in place")
    void deleteAnswers404ForForeignVersion() {
      assertThat(statusOf(pathUnderForeignDataStructure(""), HttpMethod.DELETE, null))
          .isEqualTo(HttpStatus.NOT_FOUND);

      getUnderOwnDataStructure();
    }

    @Test
    @DisplayName("Release answers 404 and leaves the version in DRAFT")
    void releaseAnswers404ForForeignVersion() {
      assertThat(statusOf(pathUnderForeignDataStructure("/release"), HttpMethod.POST, null))
          .isEqualTo(HttpStatus.NOT_FOUND);

      assertThat(getUnderOwnDataStructure().getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
    }

    @Test
    @DisplayName("Unrelease answers 404 and leaves the version released")
    void unreleaseAnswers404ForForeignVersion() {
      markVersionReleased();

      assertThat(statusOf(pathUnderForeignDataStructure("/unrelease"), HttpMethod.POST, null))
          .isEqualTo(HttpStatus.NOT_FOUND);

      assertThat(getUnderOwnDataStructure().getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Released meta PATCH answers 404 and leaves the metadata unchanged")
    void releasedMetaAnswers404ForForeignVersion() {
      markVersionReleased();

      assertThat(
              statusOf(
                  pathUnderForeignDataStructure("/released/meta"),
                  HttpMethod.PATCH,
                  Map.of("modelName", "Changed")))
          .isEqualTo(HttpStatus.NOT_FOUND);

      assertThat(getUnderOwnDataStructure().getModelName()).isEqualTo("TestModel1");
    }
  }
}
