package de.civitascore.portal.config;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.repository.DataSetRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Shared verification and polling helpers for saga integration tests. Provides methods to await
 * saga completion and verify infrastructure state (FROST, Redpanda, APISIX).
 */
public class SagaInfraVerifier {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private final DataSetRepository dataSetRepository;
  private final String frostExternalUrl;
  private final String redpandaExternalUrl;

  public SagaInfraVerifier(
      DataSetRepository dataSetRepository, String frostExternalUrl, String redpandaExternalUrl) {
    this.dataSetRepository = dataSetRepository;
    this.frostExternalUrl = frostExternalUrl;
    this.redpandaExternalUrl = redpandaExternalUrl;
  }

  /** Waits for a CREATE saga to complete: pendingSagaType=null and projectId set. */
  public DataSet awaitSagaCompletion(UUID dataSetId) {
    await()
        .atMost(120, SECONDS)
        .pollInterval(2, SECONDS)
        .untilAsserted(
            () -> {
              DataSet ds =
                  dataSetRepository
                      .findById(dataSetId)
                      .orElseThrow(() -> new AssertionError("DataSet not found"));
              assertThat(ds.getPendingSagaType())
                  .as("pendingSagaType should be null after CREATE saga completion")
                  .isNull();
              assertThat(ds.getProjectId()).as("projectId should be set after CREATE").isNotNull();
            });
    return dataSetRepository.findById(dataSetId).orElseThrow();
  }

  /** Waits for a DELETE saga to complete: pendingSagaType=null and status=READY. */
  public DataSet awaitSagaDeletion(UUID dataSetId) {
    await()
        .atMost(120, SECONDS)
        .pollInterval(2, SECONDS)
        .untilAsserted(
            () -> {
              DataSet ds =
                  dataSetRepository
                      .findById(dataSetId)
                      .orElseThrow(() -> new AssertionError("DataSet not found"));
              assertThat(ds.getPendingSagaType())
                  .as("pendingSagaType should be null after DELETE saga completion")
                  .isNull();
              assertThat(ds.getDataSetStatus())
                  .as("Status should revert to READY after DELETE saga")
                  .isEqualTo(DataSetStatus.READY);
            });
    return dataSetRepository.findById(dataSetId).orElseThrow();
  }

  public void verifyFrostProjectExists(String projectId) throws Exception {
    HttpResponse<String> response = httpGet(frostExternalUrl + "/Projects(" + projectId + ")");
    assertThat(response.statusCode())
        .as("FROST GET /Projects(%s) should return 200", projectId)
        .isEqualTo(200);
  }

  public void verifyFrostProjectDeleted(String projectId) throws Exception {
    HttpResponse<String> response = httpGet(frostExternalUrl + "/Projects(" + projectId + ")");
    assertThat(response.statusCode())
        .as("FROST GET /Projects(%s) should return 404 after deletion", projectId)
        .isEqualTo(404);
  }

  public void verifyFrostHasThings(String expectedThingName) {
    await()
        .atMost(30, SECONDS)
        .pollInterval(2, SECONDS)
        .untilAsserted(
            () -> {
              HttpResponse<String> response = httpGet(frostExternalUrl + "/Things");
              assertThat(response.statusCode()).isEqualTo(200);
              assertThat(response.body())
                  .as(
                      "FROST should contain a Thing named '%s' from pipeline data flow",
                      expectedThingName)
                  .contains(expectedThingName);
            });
  }

  public void verifyRedpandaPipelineExists(UUID pipelineId) throws Exception {
    HttpResponse<String> response = httpGet(redpandaExternalUrl + "/streams/" + pipelineId);
    assertThat(response.statusCode())
        .as("Redpanda GET /streams/%s should return 200", pipelineId)
        .isEqualTo(200);
  }

  public void verifyRedpandaPipelineDeleted(UUID pipelineId) {
    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              HttpResponse<String> response =
                  httpGet(redpandaExternalUrl + "/streams/" + pipelineId);
              assertThat(response.statusCode())
                  .as("Redpanda GET /streams/%s should return 404 after deletion", pipelineId)
                  .isEqualTo(404);
            });
  }

  public void verifyApisixReceivedRequests(
      SagaOrchestratorTestHelper sagaHelper, UUID dataSetId, String expectedMethod) {
    assertThat(sagaHelper.getApisixRequests())
        .as("APISIX mock should have received %s requests", expectedMethod)
        .isNotEmpty();

    String routeSuffix = "/apisix/admin/routes/" + dataSetId;
    assertThat(sagaHelper.getApisixRequests())
        .as(
            "APISIX mock should have received %s route request for dataset %s",
            expectedMethod, dataSetId)
        .anyMatch(r -> expectedMethod.equals(r.method()) && r.path().contains(routeSuffix));

    if ("PUT".equals(expectedMethod)) {
      String upstreamSuffix = "/apisix/admin/upstreams/" + dataSetId;
      assertThat(sagaHelper.getApisixRequests())
          .as("APISIX mock should have received PUT upstream request for dataset %s", dataSetId)
          .anyMatch(r -> "PUT".equals(r.method()) && r.path().contains(upstreamSuffix));
    }
  }

  private static HttpResponse<String> httpGet(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
