package de.civitascore.portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataSourceMetaInputDTO;
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

@RestController
@RequestMapping("/datasources")
@RequiredArgsConstructor
@Tag(name = "DataSources", description = "Data source management endpoints")
public class DataSourceController
    extends BaseDataEntityController<
        DataSourceInputDTO, DataSourceOutputDTO, DataSource, DataSourceSpec> {

  private final DataSourceService dataSourceService;
  private final DataSourceAssembler dataSourceAssembler;

  @Override
  protected DataSourceService getService() {
    return dataSourceService;
  }

  @Override
  protected DataSourceAssembler getAssembler() {
    return dataSourceAssembler;
  }

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
  @Override
  public ResponseEntity<Page<DataSourceOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataSourceSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  public ResponseEntity<DataSourceOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) throws IOException {
    DataSource entity = getService().findByIdOrThrow(id);
    DataSourceInputDTO currentDto = dataSourceAssembler.toInput(entity);
    DataSourceInputDTO patchedDto = objectMapper.readerForUpdating(currentDto).readValue(updates);
    getService().mergeConfigurationForPatch(patchedDto, entity, updates);
    DataSource updated = getService().update(id, patchedDto);
    DataSourceOutputDTO output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/publish")
  @Operation(
      summary = "Publish a data source",
      description =
          "Validates the connector configuration and transitions the data source from DRAFT to"
              + " AVAILABLE status.")
  public ResponseEntity<DataSourceOutputDTO> publish(@PathVariable UUID id) {
    DataSource published = getService().publish(id);
    DataSourceOutputDTO output = dataSourceAssembler.toOutput(published);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/unpublish")
  @Operation(
      summary = "Unpublish a data source",
      description = "Transitions the data source from AVAILABLE back to DRAFT status.")
  public ResponseEntity<DataSourceOutputDTO> unpublish(@PathVariable UUID id) {
    DataSource unpublished = getService().unpublish(id);
    DataSourceOutputDTO output = dataSourceAssembler.toOutput(unpublished);
    return ResponseEntity.ok(output);
  }

  @PutMapping("/{id}/published/meta")
  @Operation(
      summary = "Update metadata of a published data source",
      description =
          "Updates only name, description, and assignments on a data source in AVAILABLE status.")
  public ResponseEntity<DataSourceOutputDTO> updatePublishedMeta(
      @PathVariable UUID id, @Valid @RequestBody DataSourceMetaInputDTO input) {
    DataSource updated = getService().updatePublishedMeta(id, input);
    DataSourceOutputDTO output = dataSourceAssembler.toOutput(updated);
    return ResponseEntity.ok(output);
  }
}
