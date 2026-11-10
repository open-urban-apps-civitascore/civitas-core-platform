package de.civitascore.portal.service;

import de.civitascore.configadapter.model.dashboardexport.DashboardExportRequest;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult;
import de.civitascore.portal.configuration.DashboardExportProperties;
import de.civitascore.portal.messaging.kafka.DashboardExportClient;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.util.DashboardExportBusyException;
import de.civitascore.portal.util.DashboardExportRefusedException;
import de.civitascore.portal.util.DashboardNotExportableException;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

/**
 * Exports a dashboard of a dataset for sharing, through a request to config-adapter, which holds
 * the Superset session and cleans the export. OPA admits only callers who may change the dataset.
 * Every export writes one audit log line.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardExportService {

  static final String NOT_IN_SUPERSET =
      "Superset does not hold the dashboard yet. It can be shared after the dataset is released.";
  static final String SUPERSET_LOST_IT =
      "Superset does not know the dashboard. Release the dataset again to create it.";
  static final String BUSY = "Too many dashboard exports are running; try again later";
  private static final String UNKNOWN = "unknown";

  private final DashboardService dashboardService;
  private final DashboardExportProperties properties;
  private final Optional<DashboardExportClient> client;
  private final AuditorAware<UUID> currentUser;

  private final AtomicInteger running = new AtomicInteger();

  /**
   * @return the cleaned export: {@code tool} and {@code files}
   * @throws ResourceNotFoundException if the dataset has no such dashboard
   * @throws DashboardNotExportableException if Superset does not hold the dashboard
   * @throws DashboardExportRefusedException if the dashboard holds what must not be shared
   * @throws DashboardExportBusyException if too many exports wait on this instance
   * @throws ExternalSystemRejectionException if the export cannot be run
   * @throws ExternalSystemTimeoutException if no answer arrives in time
   */
  public Map<String, Object> export(UUID dataSetId, UUID dashboardId) {
    String user = currentUser.getCurrentAuditor().map(UUID::toString).orElse(UNKNOWN);
    String requestId = UUID.randomUUID().toString();
    String outcome = "ERROR";
    try {
      if (running.incrementAndGet() > properties.maxConcurrent()) {
        outcome = "BUSY";
        throw new DashboardExportBusyException(BUSY);
      }
      Map<String, Object> document = run(requestId, dataSetId, dashboardId);
      outcome = "SUCCESS";
      return document;
    } catch (ResourceNotFoundException e) {
      outcome = "NOT_FOUND";
      throw e;
    } catch (DashboardNotExportableException e) {
      outcome = "NOT_IN_SUPERSET";
      throw e;
    } catch (DashboardExportRefusedException e) {
      outcome = "REFUSED";
      throw e;
    } catch (ExternalSystemTimeoutException e) {
      outcome = "TIMEOUT";
      throw e;
    } catch (ExternalSystemRejectionException e) {
      outcome = "UNAVAILABLE";
      throw e;
    } finally {
      running.decrementAndGet();
      // TR-03187 L-1/L-6: who exported which dashboard and how it ended, without its content. The
      // request id finds the matching line in config-adapter's log.
      log.info(
          "Dashboard export: user={} requestId={} dataSet={} dashboard={} outcome={}",
          Encode.forJava(user),
          requestId,
          dataSetId,
          dashboardId,
          outcome);
    }
  }

  private Map<String, Object> run(String requestId, UUID dataSetId, UUID dashboardId) {
    Dashboard dashboard = dashboardService.findByIdAndDataSetOrThrow(dashboardId, dataSetId);
    String externalDashboardId = dashboard.getExternalDashboardId();
    if (externalDashboardId == null || externalDashboardId.isBlank()) {
      throw new DashboardNotExportableException(NOT_IN_SUPERSET);
    }
    DashboardExportClient kafka =
        client.orElseThrow(
            () ->
                new ExternalSystemRejectionException("Dashboard exports need Kafka to be enabled"));
    DashboardExportRequest request =
        new DashboardExportRequest(
            requestId, Instant.now().plus(properties.waitTime()), externalDashboardId);
    return toDocument(kafka.export(request));
  }

  static Map<String, Object> toDocument(DashboardExportResult result) {
    if (result.outcome() == DashboardExportResult.Outcome.SUCCESS) {
      if (result.document() == null) {
        throw new ExternalSystemRejectionException(
            "config-adapter answered dashboard export "
                + result.requestId()
                + " without a document");
      }
      return result.document();
    }
    if (result.reason() == null) {
      throw new ExternalSystemRejectionException(
          "config-adapter answered dashboard export " + result.requestId() + " without a reason");
    }
    // Exhaustive over Reason: a new reason fails the build here instead of passing unseen.
    throw switch (result.reason()) {
      // The portal holds an id that Superset lost; a new release creates the dashboard again.
      case NOT_FOUND -> new DashboardNotExportableException(SUPERSET_LOST_IT);
      case REFUSED -> new DashboardExportRefusedException(result.message());
      case UNAVAILABLE ->
          new ExternalSystemRejectionException(
              "config-adapter could not run dashboard export "
                  + result.requestId()
                  + ": "
                  + result.message());
    };
  }
}
