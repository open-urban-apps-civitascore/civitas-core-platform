package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSourceAssembler;
import de.civitascore.portal.repository.specification.DataSourceSpec;
import de.civitascore.portal.service.DataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing data source resources, including publish/unpublish lifecycle. */
@RestController
@RequestMapping("/datasources")
@RequiredArgsConstructor
@Tag(name = "DataSources", description = "Data source management endpoints")
public class DataSourceController
    extends BaseDataEntityController<
        DataSourceInputDTO, DataSourceOutputDTO, DataSource, DataSourceSpec> {

  private final DataSourceService dataSourceService;
  private final DataSourceAssembler dataSourceAssembler;

  /** {@inheritDoc} */
  @Override
  protected DataSourceService getService() {
    return dataSourceService;
  }

  /** {@inheritDoc} */
  @Override
  protected DataSourceAssembler getAssembler() {
    return dataSourceAssembler;
  }

  /** {@inheritDoc} */
  @Override
  protected ScopeType getScopeType() {
    return ScopeType.DATASOURCE;
  }

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "mqtt-sensor")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Temperature sensor")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "sensor")),
    @Parameter(
        name = "dataSourceStatus",
        description = "Filter by status (exact match).",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"DRAFT", "AVAILABLE"})),
    @Parameter(
        name = "connectorType",
        description = "Filter by connector type (exact match).",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"MQTT", "SQL"}))
  })
  /**
   * Retrieves a paginated list of data sources with optional filtering by name, description,
   * status, connector type, or free-text search.
   *
   * @param spec the data source search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of data source output DTOs with HTTP 200 status
   */
  @Override
  public ResponseEntity<Page<DataSourceOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataSourceSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /**
   * Publishes a data source by validating its connector configuration and transitioning status from
   * DRAFT to AVAILABLE.
   *
   * @param id the UUID of the data source to publish
   * @return the published data source output DTO with HTTP 200 status
   */
  @PostMapping("/{id}/publish")
  @Operation(
      operationId = "publishDataSource",
      summary = "Publish a data source",
      description =
          "Validates the connector configuration and transitions the data source from DRAFT to"
              + " AVAILABLE status.")
  public ResponseEntity<DataSourceOutputDTO> publish(@PathVariable UUID id) {
    DataSource published = getService().publish(id);
    DataSourceOutputDTO output = dataSourceAssembler.toOutput(published);
    return ResponseEntity.ok(output);
  }

  /**
   * Unpublishes a data source by transitioning it from AVAILABLE back to DRAFT status.
   *
   * @param id the UUID of the data source to unpublish
   * @return the unpublished data source output DTO with HTTP 200 status
   */
  @PostMapping("/{id}/unpublish")
  @Operation(
      operationId = "unpublishDataSource",
      summary = "Unpublish a data source",
      description = "Transitions the data source from AVAILABLE back to DRAFT status.")
  public ResponseEntity<DataSourceOutputDTO> unpublish(@PathVariable UUID id) {
    DataSource unpublished = getService().unpublish(id);
    DataSourceOutputDTO output = dataSourceAssembler.toOutput(unpublished);
    return ResponseEntity.ok(output);
  }

  /**
   * Updates the metadata of a published data source in AVAILABLE status.
   *
   * @param id the UUID of the published data source
   * @param input the validated data source input DTO containing updated metadata
   * @return the updated data source output DTO with HTTP 200 status
   */
  @PutMapping("/{id}/published/meta")
  @Operation(
      operationId = "updateDataSourcePublishedMeta",
      summary = "Update metadata of a published data source",
      description =
          "Updates name, description, connector configuration, and assignments on a data source in"
              + " AVAILABLE status.")
  public ResponseEntity<DataSourceOutputDTO> updatePublishedMeta(
      @PathVariable UUID id, @Valid @RequestBody DataSourceInputDTO input) {
    DataSource updated = getService().updatePublishedMeta(id, input);
    DataSourceOutputDTO output = dataSourceAssembler.toOutput(updated);
    return ResponseEntity.ok(output);
  }

  /**
   * Merges connector-specific configuration fields during a JSON-merge patch.
   *
   * <p>Delegates to {@link DataSourceService#mergeConfigurationForPatch} after the standard Jackson
   * merge so that nested connector properties are handled correctly.
   *
   * <p>{@inheritDoc}
   */
  @Override
  protected DataSourceInputDTO patchInput(
      DataSourceInputDTO currentDto, DataSource entity, JsonNode updates) throws IOException {
    DataSourceInputDTO patchedDto = super.patchInput(currentDto, entity, updates);
    getService().mergeConfigurationForPatch(patchedDto, entity, updates);
    return patchedDto;
  }
}
