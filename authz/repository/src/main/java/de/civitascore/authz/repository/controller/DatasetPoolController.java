package de.civitascore.authz.repository.controller;

import de.civitascore.authz.repository.model.dto.DatasetPoolResponse;
import de.civitascore.authz.repository.service.DatasetPoolService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing dataset→datapool membership.
 *
 * <p>Consumed by OPA to apply Epic 1 union inheritance (a DATAPOOL-scoped grant applies to every
 * dataset in that pool). Returns the single pool id a dataset belongs to, or {@code poolId: null}
 * when it has no pool, or 404 when the dataset does not exist.
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
}
