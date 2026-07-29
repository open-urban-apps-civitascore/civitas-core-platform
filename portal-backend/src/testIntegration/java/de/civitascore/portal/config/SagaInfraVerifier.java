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
 * saga completion and verify infrastructure state (FROST).
 */
public class SagaInfraVerifier {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private final DataSetRepository dataSetRepository;
  private final String frostExternalUrl;

  public SagaInfraVerifier(DataSetRepository dataSetRepository, String frostExternalUrl) {
    this.dataSetRepository = dataSetRepository;
    this.frostExternalUrl = frostExternalUrl;
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

  /** Waits for an UPDATE saga to complete: pendingSagaType=null and status still AVAILABLE. */
  public DataSet awaitSagaUpdate(UUID dataSetId) {
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
                  .as("pendingSagaType should be null after UPDATE saga completion")
                  .isNull();
              assertThat(ds.getDataSetStatus())
                  .as("Status should remain AVAILABLE after UPDATE saga")
                  .isEqualTo(DataSetStatus.AVAILABLE);
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

  private static HttpResponse<String> httpGet(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
