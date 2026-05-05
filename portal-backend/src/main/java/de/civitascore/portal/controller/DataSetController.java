package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSetAssembler;
import de.civitascore.portal.repository.specification.DataSetSpec;
import de.civitascore.portal.service.DataSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing dataset resources, including release lifecycle operations. */
@RestController
@RequestMapping("/datasets")
@RequiredArgsConstructor
@Tag(name = "DataSets", description = "Dataset management endpoints")
public class DataSetController
    extends BaseDataEntityController<DataSetInputDTO, DataSetOutputDTO, DataSet, DataSetSpec> {

  private final DataSetService dataSetService;
  private final DataSetAssembler dataSetAssembler;

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
  /**
   * Retrieves a paginated list of datasets with optional filtering by name, description, or
   * free-text search.
   *
   * @param spec the dataset search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of dataset output DTOs with HTTP 200 status
   */
  @Override
  public ResponseEntity<Page<DataSetOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataSetSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /** {@inheritDoc} */
  @Override
  protected DataSetService getService() {
    return dataSetService;
  }

  /** {@inheritDoc} */
  @Override
  protected DataSetAssembler getAssembler() {
    return dataSetAssembler;
  }

  /**
   * Updates a dataset in DRAFT status by fully replacing its content.
   *
   * @param id the UUID of the dataset to update
   * @param input the validated dataset input DTO
   * @return the updated dataset output DTO with HTTP 200 status
   */
  @Override
  @PutMapping("/{id}")
  @Operation(
      summary = "Update a DRAFT dataset",
      description =
          "Updates a dataset in DRAFT status. For released datasets (READY or AVAILABLE), use PUT /datasets/{id}/released/meta instead.")
  public ResponseEntity<DataSetOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody DataSetInputDTO input) {
    return super.update(id, input);
  }

  /** {@inheritDoc} */
  @Override
  protected ScopeType getScopeType() {
    return ScopeType.DATASET;
  }

  @PostMapping("/{id}/stage")
  @Operation(
      operationId = "stageDataSet",
      summary = "Stage a dataset",
      description =
          "Validates the dataset and generates distributions from pipeline APIs, transitioning status from DRAFT to READY.")
  public ResponseEntity<DataSetOutputDTO> stage(@PathVariable UUID id) {
    DataSet ready = dataSetService.stage(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(ready);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/unstage")
  @Operation(
      operationId = "unstageDataSet",
      summary = "Unstage a dataset",
      description =
          "Removes auto-generated distributions and reverts the dataset from READY to DRAFT.")
  public ResponseEntity<DataSetOutputDTO> unstage(@PathVariable UUID id) {
    DataSet draft = dataSetService.unstage(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(draft);
    return ResponseEntity.ok(output);
  }

  @Override
  public ResponseEntity<DataSetOutputDTO> release(@PathVariable UUID id) {
    DataSet released = dataSetService.release(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(released);
    return ResponseEntity.accepted().body(output);
  }

  @Override
  @ApiResponse(
      responseCode = "409",
      description = "Conflict (saga is in-flight for this dataset)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataSetOutputDTO> unrelease(@PathVariable UUID id) {
    DataSet unreleased = dataSetService.unrelease(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(unreleased);
    return ResponseEntity.accepted().body(output);
  }

  @Override
  @ApiResponse(
      responseCode = "409",
      description = "Conflict (saga is in-flight for this dataset)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataSetOutputDTO> updateReleasedMeta(
      @PathVariable UUID id, @Valid @RequestBody DataSetInputDTO input) {
    DataSetInputDTO preProcessedInput = preProcessInput(input);
    DataSet updated = dataSetService.updateReleasedMeta(id, preProcessedInput);
    DataSetOutputDTO output = dataSetAssembler.toOutput(updated);
    return ResponseEntity.ok(output);
  }

  /**
   * Deletes a DRAFT dataset. READY or released datasets must be unstaged/unreleased first.
   *
   * @param id the UUID of the dataset to delete
   */
  @Override
  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete a dataset",
      description =
          "Deletes a DRAFT dataset immediately (204 No Content). "
              + "READY datasets cannot be deleted — unstage first (POST /{id}/unstage). "
              + "AVAILABLE datasets cannot be deleted directly — unrelease first (POST /{id}/unrelease) to tear down infrastructure, then delete.")
  public void delete(@PathVariable UUID id) {
    dataSetService.deleteById(id);
  }
}
