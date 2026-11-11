package de.civitascore.portal.controller;

import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.ConnectionTestInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataSourceMetaInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.ConnectionTestResultDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSourceAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.DataSourceSpec;
import de.civitascore.portal.service.DataSourceService;
import de.civitascore.portal.service.connector.DataSourceConnectionTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** REST controller for managing data source resources, including release/unrelease lifecycle. */
@RestController
@RequestMapping("/datasources")
@RequiredArgsConstructor
@Tag(name = "Data sources", description = "Data source management endpoints")
public class DataSourceController {

  private final DataSourceService dataSourceService;
  private final DataSourceAssembler dataSourceAssembler;
  private final DataSourceConnectionTestService connectionTestService;
  private final JsonMergePatch jsonMergePatch;
  private final ScopeAssignments scopeAssignments;

  @GetMapping
  @Operation(summary = "List all data sources")
  public Page<DataSourceOutputDTO> listDataSources(
      @ParameterObject DataSourceSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return dataSourceAssembler.toOutput(dataSourceService.findAll(spec, pageable));
  }

  @GetMapping("/{dataSourceId}")
  @Operation(summary = "Get a data source by ID")
  @Problems(ProblemType.NOT_FOUND)
  public DataSourceOutputDTO getDataSource(@PathVariable UUID dataSourceId) {
    return dataSourceAssembler.toOutput(dataSourceService.findByIdOrThrow(dataSourceId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new data source")
  @Problems({ProblemType.INVALID_INPUT, ProblemType.NOT_FOUND, ProblemType.REGISTRY_UNAVAILABLE})
  public ResponseEntity<DataSourceOutputDTO> createDataSource(
      @Valid @RequestBody DataSourceInputDTO input) {
    DataSource created = dataSourceService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(dataSourceAssembler.toOutput(created));
  }

  @PutMapping("/{dataSourceId}")
  @Operation(summary = "Replace a data source")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSourceOutputDTO updateDataSource(
      @PathVariable UUID dataSourceId, @Valid @RequestBody DataSourceInputDTO input) {
    return dataSourceAssembler.toOutput(dataSourceService.update(dataSourceId, input));
  }

  @PatchMapping("/{dataSourceId}")
  @Operation(
      summary = "Partially update a data source",
      description =
          JsonMergePatch.OPERATION_DESCRIPTION
              + " The connector is the exception: it merges recursively into the stored"
              + " connector, so a secret that the patch omits keeps its stored value. A connector"
              + " with another connectorType replaces the stored one.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.VALIDATION_FAILED,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSourceOutputDTO patchDataSource(
      @PathVariable UUID dataSourceId, @RequestBody JsonNode updates) {
    DataSource current = dataSourceService.findByIdOrThrow(dataSourceId);
    return dataSourceAssembler.toOutput(
        dataSourceService.update(dataSourceId, patched(current, updates)));
  }

  private DataSourceInputDTO patched(DataSource current, JsonNode updates) {
    JsonNode otherMembers =
        updates instanceof ObjectNode object
            ? object.deepCopy().without(ConnectorJson.ROOT)
            : updates;
    DataSourceInputDTO patched =
        jsonMergePatch.apply(dataSourceAssembler.toInput(current), otherMembers, current.getId());
    patched.setConnector(
        dataSourceService.patchConnector(current, updates.path(ConnectorJson.ROOT)));
    return patched;
  }

  @DeleteMapping("/{dataSourceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a data source")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE, ProblemType.REGISTRY_UNAVAILABLE})
  public void deleteDataSource(@PathVariable UUID dataSourceId) {
    dataSourceService.deleteById(dataSourceId);
  }

  @GetMapping("/{dataSourceId}/assignments")
  @Operation(
      summary = "List the assignments of a data source",
      description = "Returns all role assignments scoped to this data source.")
  @Problems(ProblemType.NOT_FOUND)
  public List<AssignmentOutputDTO> getDataSourceAssignments(@PathVariable UUID dataSourceId) {
    return scopeAssignments.of(
        ScopeType.DATASOURCE, dataSourceService.findByIdOrThrow(dataSourceId));
  }

  @PostMapping("/{dataSourceId}/release")
  @Operation(
      summary = "Release a data source",
      description = "Transitions the data source from DRAFT to AVAILABLE status.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.VALIDATION_FAILED,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSourceOutputDTO releaseDataSource(@PathVariable UUID dataSourceId) {
    return dataSourceAssembler.toOutput(dataSourceService.release(dataSourceId));
  }

  @PostMapping("/{dataSourceId}/unrelease")
  @Operation(
      summary = "Unrelease a data source",
      description = "Transitions the data source from AVAILABLE back to DRAFT status.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE})
  public DataSourceOutputDTO unreleaseDataSource(@PathVariable UUID dataSourceId) {
    return dataSourceAssembler.toOutput(dataSourceService.unrelease(dataSourceId));
  }

  @PatchMapping("/{dataSourceId}/released/meta")
  @Operation(
      summary = "Update metadata of a released data source",
      description =
          "Applies a JSON merge patch to the metadata of a released data source. An omitted field"
              + " keeps its value. A field that is not metadata answers 400, and so does null for"
              + " assignments or datapoolScope. Only works on data sources that are not in DRAFT"
              + " status.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = @Content(schema = @Schema(implementation = DataSourceMetaInputDTO.class)))
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.VALIDATION_FAILED,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSourceOutputDTO updateReleasedDataSourceMeta(
      @PathVariable UUID dataSourceId, @RequestBody JsonNode updates) {
    DataSourceMetaInputDTO current =
        dataSourceService.toMetaInput(dataSourceService.findByIdOrThrow(dataSourceId));
    DataSourceMetaInputDTO meta = jsonMergePatch.apply(current, updates, dataSourceId);
    return dataSourceAssembler.toOutput(dataSourceService.updateReleasedMeta(dataSourceId, meta));
  }

  @PostMapping("/connection-test")
  @Operation(
      summary = "Test connector values",
      description =
          "Tests whether the source answers with the given connector values, before a data source"
              + " holds them. The values must meet the rules of a release. SQL reads up to 5 rows"
              + " of the table, MQTT subscribes to the topic, HTTP runs the named API call. The"
              + " test changes no data source and stores nothing. A placeholder \"********\" has"
              + " no stored secret to keep here and answers 400. A user runs one test at a time."
              + " The answer tells with success whether the source answered as configured, with"
              + " reason why not, and with preview what it returned.")
  @Problems({
    ProblemType.TOO_MANY_REQUESTS,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public ConnectionTestResultDTO testDataSourceConnection(
      @Valid @RequestBody ConnectionTestInputDTO input) {
    return connectionTestService.test(input);
  }

  @PostMapping("/{dataSourceId}/connection-test")
  @Operation(
      summary = "Test connector values of a saved data source",
      description =
          "Tests whether the source answers with the given connector values, which may differ from"
              + " the stored ones, for a data source in any status. A placeholder \"********\""
              + " keeps the stored secret only to test the connector as saved; with changed values,"
              + " or where no secret is stored, it answers 400. Values for another destination"
              + " need DATASOURCE_READ for the whole tenant, otherwise it answers 403. The values"
              + " must meet the rules of a release. The test changes no data source and stores"
              + " nothing. A user runs one test at a time. The answer tells with success whether"
              + " the source answered as configured, with reason why not, and with preview what it"
              + " returned.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.FORBIDDEN,
    ProblemType.TOO_MANY_REQUESTS,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public ConnectionTestResultDTO testSavedDataSourceConnection(
      @PathVariable UUID dataSourceId, @Valid @RequestBody ConnectionTestInputDTO input) {
    return connectionTestService.test(dataSourceId, input);
  }
}
