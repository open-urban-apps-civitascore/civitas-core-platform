package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.model.dashboardexport.DashboardExportRequest;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult.Reason;
import de.civitascore.portal.configuration.DashboardExportProperties;
import de.civitascore.portal.messaging.kafka.DashboardExportClient;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.util.DashboardExportBusyException;
import de.civitascore.portal.util.DashboardExportRefusedException;
import de.civitascore.portal.util.DashboardNotExportableException;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardExportService Tests")
class DashboardExportServiceTest {

  private static final UUID DATA_SET = UUID.randomUUID();
  private static final UUID DASHBOARD = UUID.randomUUID();
  private static final Map<String, Object> DOCUMENT =
      Map.of("tool", "superset", "files", Map.of("metadata.yaml", Map.of("version", "1.0.0")));

  @Mock private DashboardService dashboardService;
  @Mock private DashboardExportClient client;
  @Mock private AuditorAware<UUID> currentUser;

  @BeforeEach
  void setUp() {
    lenient().when(currentUser.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
  }

  private DashboardExportService service(int maxConcurrent, boolean kafka) {
    return new DashboardExportService(
        dashboardService,
        new DashboardExportProperties("request", "result", 30, maxConcurrent),
        kafka ? Optional.of(client) : Optional.empty(),
        currentUser);
  }

  private void dashboardInSuperset(String externalDashboardId) {
    Dashboard dashboard = mock(Dashboard.class);
    when(dashboard.getExternalDashboardId()).thenReturn(externalDashboardId);
    when(dashboardService.findByIdAndDataSetOrThrow(DASHBOARD, DATA_SET)).thenReturn(dashboard);
  }

  private void answer(DashboardExportResult.Outcome outcome, Reason reason, String message) {
    when(client.export(any()))
        .thenAnswer(
            invocation -> {
              DashboardExportRequest request = invocation.getArgument(0);
              return outcome == DashboardExportResult.Outcome.SUCCESS
                  ? DashboardExportResult.success(request.requestId(), DOCUMENT)
                  : DashboardExportResult.failure(request.requestId(), reason, message);
            });
  }

  @Test
  @DisplayName("an export of a dashboard in Superset returns the cleaned document")
  void export_ofADashboardInSuperset_returnsTheDocument() {
    dashboardInSuperset("7");
    AtomicReference<DashboardExportRequest> sent = new AtomicReference<>();
    when(client.export(any()))
        .thenAnswer(
            invocation -> {
              sent.set(invocation.getArgument(0));
              return DashboardExportResult.success(sent.get().requestId(), DOCUMENT);
            });

    assertThat(service(2, true).export(DATA_SET, DASHBOARD)).isEqualTo(DOCUMENT);
    assertThat(sent.get().externalDashboardId()).isEqualTo("7");
    assertThat(sent.get().expiresAt()).isAfter(Instant.now());
  }

  @Test
  @DisplayName("a dashboard Superset does not hold yet is not exported and sends nothing")
  void export_ofADashboardWithoutSupersetId_isNotExportable() {
    dashboardInSuperset(null);

    assertThatThrownBy(() -> service(2, true).export(DATA_SET, DASHBOARD))
        .isInstanceOf(DashboardNotExportableException.class)
        .hasMessage(DashboardExportService.NOT_IN_SUPERSET);
    verifyNoInteractions(client);
  }

  @Test
  @DisplayName("a dashboard of another dataset reads as absent")
  void export_ofAForeignDashboard_isNotFound() {
    when(dashboardService.findByIdAndDataSetOrThrow(DASHBOARD, DATA_SET))
        .thenThrow(new ResourceNotFoundException("Dashboard", DASHBOARD));

    assertThatThrownBy(() -> service(2, true).export(DATA_SET, DASHBOARD))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(client);
  }

  @Test
  @DisplayName("a refusal keeps the message of config-adapter")
  void export_refused_keepsTheMessage() {
    dashboardInSuperset("7");
    answer(DashboardExportResult.Outcome.FAILURE, Reason.REFUSED, "reads with its own SQL query");

    assertThatThrownBy(() -> service(2, true).export(DATA_SET, DASHBOARD))
        .isInstanceOf(DashboardExportRefusedException.class)
        .hasMessage("reads with its own SQL query");
  }

  @Test
  @DisplayName("a dashboard Superset has lost asks for a new release")
  void export_notFoundInSuperset_isNotExportable() {
    dashboardInSuperset("7");
    answer(DashboardExportResult.Outcome.FAILURE, Reason.NOT_FOUND, "unknown");

    assertThatThrownBy(() -> service(2, true).export(DATA_SET, DASHBOARD))
        .isInstanceOf(DashboardNotExportableException.class)
        .hasMessage(DashboardExportService.SUPERSET_LOST_IT);
  }

  @Test
  @DisplayName("an export config-adapter cannot run is a rejection of the external system")
  void export_unavailable_isARejection() {
    dashboardInSuperset("7");
    answer(DashboardExportResult.Outcome.FAILURE, Reason.UNAVAILABLE, "failed");

    assertThatThrownBy(() -> service(2, true).export(DATA_SET, DASHBOARD))
        .isInstanceOf(ExternalSystemRejectionException.class);
  }

  @Test
  @DisplayName("without Kafka no export can run")
  void export_withoutKafka_isARejection() {
    dashboardInSuperset("7");

    assertThatThrownBy(() -> service(2, false).export(DATA_SET, DASHBOARD))
        .isInstanceOf(ExternalSystemRejectionException.class);
  }

  @Test
  @DisplayName("beyond max-concurrent an export is busy before the database is read")
  void export_beyondTheLimit_isBusy() {
    assertThatThrownBy(() -> service(0, true).export(DATA_SET, DASHBOARD))
        .isInstanceOf(DashboardExportBusyException.class);
    verifyNoInteractions(dashboardService, client);
  }
}
