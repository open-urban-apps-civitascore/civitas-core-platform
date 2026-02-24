package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.model.output.assembler.DataSetAssembler;
import de.civitascore.portal.repository.specification.DataSetSpec;
import de.civitascore.portal.service.AssignmentService;
import de.civitascore.portal.service.DataSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datasets")
@RequiredArgsConstructor
@Tag(name = "DataSets", description = "Dataset management endpoints")
public class DataSetController
    extends BaseController<DataSetInputDTO, DataSetOutputDTO, DataSet, DataSetSpec> {

  private final DataSetService dataSetService;
  private final DataSetAssembler dataSetAssembler;
  private final AssignmentService assignmentService;
  private final AssignmentAssembler assignmentAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "sensor-data")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Temperature sensor readings")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "sensor"))
  })
  @Override
  public ResponseEntity<Page<DataSetOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataSetSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(applyScopeFilter(spec), pageable);
  }

  @Override
  protected DataSetService getService() {
    return dataSetService;
  }

  @Override
  protected DataSetAssembler getAssembler() {
    return dataSetAssembler;
  }

  @Override
  @PutMapping("/{id}")
  @Operation(
      summary = "Update a DRAFT dataset",
      description =
          "Updates a dataset in DRAFT status. For published datasets (READY or AVAILABLE), use PUT /datasets/{id}/published/meta instead.")
  public ResponseEntity<DataSetOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody DataSetInputDTO input) {
    return super.update(id, input);
  }

  @PutMapping("/{id}/published/meta")
  @Operation(
      summary = "Update metadata of a published dataset",
      description =
          "Updates only the metadata (name, description) of a published dataset (READY or AVAILABLE status). Cannot modify persistenceId or pipelines. For DRAFT datasets, use PUT /datasets/{id} instead.")
  public ResponseEntity<DataSetOutputDTO> updatePublishedMeta(
      @PathVariable UUID id, @Valid @RequestBody DataSetInputDTO input) {
    DataSetInputDTO preProcessedInput = preProcessInput(input);
    DataSet updated = dataSetService.updatePublishedMeta(id, preProcessedInput);
    DataSetOutputDTO output = dataSetAssembler.toOutput(updated);
    return ResponseEntity.ok(output);
  }

  @GetMapping("/{id}/assignments")
  @Operation(
      summary = "Get assignments for a dataset",
      description = "Returns all role assignments scoped to the specified dataset.")
  public ResponseEntity<List<AssignmentOutputDTO>> getAssignments(@PathVariable UUID id) {
    getService().findByIdOrThrow(id);
    List<Assignment> assignments =
        assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASET, id);
    List<AssignmentOutputDTO> output =
        assignments.stream().map(assignmentAssembler::toOutput).toList();
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/publish")
  @Operation(
      summary = "Publish a dataset",
      description =
          "Publishes a dataset by generating distributions from pipeline APIs and setting status to READY. Requires at least one pipeline to be present in the dataset.")
  public ResponseEntity<DataSetOutputDTO> publishDataSet(@PathVariable UUID id) {
    DataSet published = dataSetService.publish(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(published);
    return ResponseEntity.ok(output);
  }
}
