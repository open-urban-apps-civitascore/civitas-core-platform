package de.civitascore.authz.repository.controller;

import de.civitascore.authz.repository.model.dto.DataSourcePoolsResponse;
import de.civitascore.authz.repository.service.DataSourcePoolsService;
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
 * REST controller exposing which datapools a data source is assigned to.
 *
 * <p>Consumed by OPA to apply datapool inheritance to data sources (a DATAPOOL-scoped grant conveys
 * read access to the data sources assigned to that pool). Returns 404 when the data source does not
 * exist.
 *
 * <p>Like {@link UserContextController}, this endpoint has no application-level authentication —
 * security is enforced via network policies and Linkerd mTLS (see deployment docs).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class DataSourcePoolsController {

  private static final String UUID_PATTERN =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  private final DataSourcePoolsService dataSourcePoolsService;

  @GetMapping("/datasource-pools/{dataSourceId}")
  public ResponseEntity<DataSourcePoolsResponse> getDataSourcePools(
      @PathVariable
          @NotBlank
          @Pattern(regexp = UUID_PATTERN, message = "dataSourceId must be a valid UUID")
          String dataSourceId) {
    return dataSourcePoolsService
        .getDataSourcePools(UUID.fromString(dataSourceId))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
