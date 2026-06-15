package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.VisualizationType;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.model.output.DashboardOutputDTO;
import de.civitascore.portal.repository.DashboardRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.RestPage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("Visualization Controller Integration Tests")
class VisualizationControllerIntegrationTest
    extends BaseControllerIntegrationTest<DashboardInputDTO, DashboardOutputDTO> {

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private DashboardRepository dashboardRepository;
  @Autowired private DataSetRepository dataSetRepository;

  private UUID datasetId;

  private void ensurePrerequisites() {
    if (datasetId == null || dataSetRepository.findById(datasetId).isEmpty()) {
      DataSet ds = portalData.dataSet();
      datasetId = ds.getId();
    }
  }

  @Override
  protected String getEndpointPath() {
    ensurePrerequisites();
    return "/datasets/" + datasetId + "/visualizations";
  }

  @Override
  protected void performAdditionalCleanup() {
    dashboardRepository.deleteAll();
    portalData.cleanAll();
    datasetId = null;
  }

  @Override
  protected DashboardInputDTO createValidInput() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic Dashboard");
    input.setDescription("city traffic");
    return input;
  }

  @Override
  protected DashboardInputDTO createInvalidInput() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName(""); // @NotBlank violation
    return input;
  }

  @Override
  protected DashboardInputDTO createUpdateInput() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic Dashboard v2");
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DashboardOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DashboardOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DashboardOutputDTO output) {
    return output.getId();
  }

  // PATCH is not supported — override base tests to expect 405

  @Override
  @Test
  @DisplayName("PATCH is not supported — always returns 405")
  void shouldRejectPatchThatResultsInInvalidEntity() {
    UUID id = createTestEntity();
    ResponseEntity<DashboardOutputDTO> response =
        performPatch(id, objectMapper.convertValue(createInvalidInput(), Object.class));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @Override
  @Test
  @DisplayName("PATCH is not supported — always returns 405")
  void shouldReturn404WhenPatchingNonExistentEntity() {
    UUID randomId = UUID.randomUUID();
    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + randomId,
            HttpMethod.PATCH,
            createAuthHeaders(),
            Map.of("name", "x"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  // ── Dashboard-specific tests ───────────────────────────────────────────────

  @Test
  @DisplayName("Create sets datasetId, active=true and visualizationType=DASHBOARD")
  void createReturnsDiscriminatorDatasetAndActive() {
    ensurePrerequisites();
    ResponseEntity<DashboardOutputDTO> response = performCreate(createValidInput());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    DashboardOutputDTO body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getDatasetId()).isEqualTo(datasetId);
    assertThat(body.isActive()).isTrue();
    assertThat(body.getVisualizationType()).isEqualTo(VisualizationType.DASHBOARD);
  }

  @Test
  @DisplayName("DELETE hard-deletes: row is physically gone and GET by id returns 404")
  void deleteHardDeletes() {
    UUID id = createTestEntity();

    ResponseEntity<Void> deleteResponse = performDelete(id);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<ProblemDetail> getResponse =
        exchangeForProblem(getEndpointPath() + "/" + id, HttpMethod.GET, createAuthHeaders(), null);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /{id} under a foreign datasetId returns 404")
  void getByIdWithForeignDatasetReturns404() {
    UUID id = createTestEntity();
    UUID foreignDatasetId = UUID.randomUUID();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            "/datasets/" + foreignDatasetId + "/visualizations/" + id,
            HttpMethod.GET,
            createAuthHeaders(),
            null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Deleted dashboard is absent from the collection list (row physically removed)")
  void deletedDashboardAbsentFromList() {
    UUID id = createTestEntity();

    ResponseEntity<Void> deleteResponse = performDelete(id);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<RestPage<DashboardOutputDTO>> listResponse = performGetAll();
    assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listResponse.getBody()).isNotNull();
    List<UUID> ids =
        listResponse.getBody().getContent().stream().map(DashboardOutputDTO::getId).toList();
    assertThat(ids).doesNotContain(id);
  }

  @Test
  @DisplayName("GET collection under a foreign datasetId returns an empty page (spec filter)")
  void listUnderForeignDatasetReturnsEmpty() {
    createTestEntity(); // belongs to this.datasetId, not the foreign one queried below

    UUID foreignDatasetId = UUID.randomUUID();
    ResponseEntity<RestPage<DashboardOutputDTO>> response =
        exchange(
            buildUri("/datasets/" + foreignDatasetId + "/visualizations", Map.of()),
            HttpMethod.GET,
            createAuthHeaders(),
            getPageTypeReference());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContent()).isEmpty();
  }

  @Test
  @DisplayName("PUT preserves an engine-sync-set active=false (the mapper must not clobber it)")
  void updatePreservesActiveFalse() {
    UUID id = createTestEntity();

    // active is engine-sync-owned and not client-writable, so flip it via the repository to model
    // a dashboard the engine has marked inactive.
    Dashboard dashboard = dashboardRepository.findById(id).orElseThrow();
    dashboard.setActive(false);
    dashboardRepository.saveAndFlush(dashboard);

    ResponseEntity<DashboardOutputDTO> updateResponse = performUpdate(id, createUpdateInput());
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updateResponse.getBody()).isNotNull();
    assertThat(updateResponse.getBody().getName()).isEqualTo("Traffic Dashboard v2");
    assertThat(updateResponse.getBody().isActive()).isFalse();

    assertThat(dashboardRepository.findById(id).orElseThrow().isActive()).isFalse();
  }

  @Test
  @DisplayName("PUT under a foreign datasetId returns 404 and does not mutate the dashboard")
  void updateUnderForeignDatasetReturns404() {
    UUID id = createTestEntity();
    UUID foreignDatasetId = UUID.randomUUID();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            "/datasets/" + foreignDatasetId + "/visualizations/" + id,
            HttpMethod.PUT,
            createAuthHeaders(),
            createUpdateInput());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(dashboardRepository.findById(id).orElseThrow().getName())
        .isEqualTo("Traffic Dashboard");
  }

  @Test
  @DisplayName("DELETE under a foreign datasetId returns 404 and leaves the row in place")
  void deleteUnderForeignDatasetReturns404() {
    UUID id = createTestEntity();
    UUID foreignDatasetId = UUID.randomUUID();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            "/datasets/" + foreignDatasetId + "/visualizations/" + id,
            HttpMethod.DELETE,
            createAuthHeaders(),
            null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(dashboardRepository.findById(id)).isPresent();
  }
}
