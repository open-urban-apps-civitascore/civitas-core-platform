package de.civitascore.authz.repository.controller;

import de.civitascore.authz.repository.model.dto.DatapoolDatasetSchema;
import de.civitascore.authz.repository.model.dto.DatapoolDatasetsBatchRequest;
import de.civitascore.authz.repository.model.dto.DatapoolDatasetsResponse;
import de.civitascore.authz.repository.model.dto.DatasetPoolResponse;
import de.civitascore.authz.repository.service.DatasetPoolService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
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
 * REST controller exposing dataset↔datapool membership, in both directions.
 *
 * <p>Consumed by OPA to check dataset pool membership and public access. A DATAPOOL-scoped grant
 * applies to every dataset in that pool. The single-dataset lookup returns the pool id, public
 * access flag, and release state, or 404 when the dataset does not exist. The batch lookup is the
 * reverse: given a set of pools, every dataset that belongs to any of them.
 *
 * <p>Like {@link UserContextController}, this endpoint has no application-level authentication —
 * security is enforced via network policies and Linkerd mTLS (see deployment docs).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class DatasetPoolController {

  private static final String UUID_PATTERN =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  private final DatasetPoolService datasetPoolService;

  @GetMapping("/dataset-pool/{datasetId}")
  public ResponseEntity<DatasetPoolResponse> getDatasetPool(
      @PathVariable
          @NotBlank
          @Pattern(regexp = UUID_PATTERN, message = "datasetId must be a valid UUID")
          String datasetId) {
    return datasetPoolService
        .getDatasetPool(UUID.fromString(datasetId))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/dataset-pool/datasets")
  public ResponseEntity<DatapoolDatasetsResponse> getDatasetsInPools(
      @Valid @RequestBody DatapoolDatasetsBatchRequest request) {
    List<UUID> poolIds = request.getPoolIds().stream().map(UUID::fromString).toList();
    return ResponseEntity.ok(datasetPoolService.getDatasetsInPools(poolIds));
  }

  @GetMapping("/dataset-pool/schema/{schemaName}")
  public ResponseEntity<DatasetPoolResponse> getDatasetBySchema(
      @PathVariable @NotBlank @Pattern(regexp = "^[a-z0-9_]{1,63}$") String schemaName) {
    return datasetPoolService
        .getDatasetBySchema(schemaName)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/dataset-pool/schemas")
  public ResponseEntity<List<DatapoolDatasetSchema>> getDatasetSchemas() {
    return ResponseEntity.ok(datasetPoolService.getDatasetSchemas());
  }
}
