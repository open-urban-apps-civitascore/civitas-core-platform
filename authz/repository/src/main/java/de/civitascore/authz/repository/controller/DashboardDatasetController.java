package de.civitascore.authz.repository.controller;

import de.civitascore.authz.repository.model.dto.DashboardDatasetBatchItem;
import de.civitascore.authz.repository.model.dto.DashboardDatasetBatchRequest;
import de.civitascore.authz.repository.model.dto.DashboardDatasetResponse;
import de.civitascore.authz.repository.service.DashboardDatasetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing dashboard→dataset resolution.
 *
 * <p>Consumed directly by civitas_security.py (not via OPA/Rego, unlike {@link
 * DatasetPoolController}) to resolve which Civitas dataset a Superset dashboard belongs to, keyed
 * by the dashboard's own engine-side id rather than a copy of the dataset id stored in the
 * dashboard's own metadata.
 *
 * <p>Like {@link DatasetPoolController} and {@link UserContextController}, this endpoint has no
 * application-level authentication — security is enforced via network policies and Linkerd mTLS
 * (see deployment docs).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class DashboardDatasetController {

  private static final String EXTERNAL_DASHBOARD_ID_PATTERN = "^[0-9]+$";

  private final DashboardDatasetService dashboardDatasetService;

  @GetMapping("/dashboard-dataset/{externalDashboardId}")
  public ResponseEntity<DashboardDatasetResponse> getDashboardDataset(
      @PathVariable
          @NotBlank
          @Pattern(
              regexp = EXTERNAL_DASHBOARD_ID_PATTERN,
              message = "externalDashboardId must be a non-negative integer")
          String externalDashboardId) {
    return dashboardDatasetService
        .getDashboardDataset(externalDashboardId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/dashboard-dataset/batch")
  public ResponseEntity<List<DashboardDatasetBatchItem>> getDashboardDatasets(
      @Valid @RequestBody DashboardDatasetBatchRequest request) {
    return ResponseEntity.ok(
        dashboardDatasetService.getDashboardDatasets(request.getExternalDashboardIds()));
  }
}
