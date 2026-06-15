package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.NamedApiOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSetAssembler;
import de.civitascore.portal.repository.specification.DataSetSpec;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.service.DataSetService;
import de.civitascore.portal.util.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
   * Lists the named APIs of a dataset (issue #1596, concept #1379). Returns the same {@link
   * NamedApiOutputDTO} list embedded in {@code GET /datasets/{id}} — including the server-built
   * {@code previewUrl} — via a dedicated discovery surface. Respects the caller's {@code
   * X-Allowed-Scope-Ids}: an unknown or out-of-scope dataset yields 404, so route existence is not
   * leaked.
   *
   * <p>Per concept #1379 this is a consumer-facing discovery surface for <b>active, published</b>
   * APIs: it returns the named APIs only when the dataset is published (lifecycle status {@code
   * AVAILABLE}). For a {@code DRAFT}/{@code READY} dataset it returns an empty list — draft edits
   * must not change the public API surface. (Scope is still enforced first: an unknown/out-of-scope
   * dataset yields 404 regardless of status, so route existence is not leaked.)
   *
   * @param id the dataset UUID
   * @return HTTP 200 with the published dataset's named APIs (empty unless the dataset is
   *     AVAILABLE)
   */
  @GetMapping("/{id}/apis")
  @Operation(
      operationId = "getDataSetApis",
      summary = "List a dataset's published named APIs",
      description =
          "Returns the named APIs of a PUBLISHED dataset (lifecycle status AVAILABLE), including the"
              + " server-built previewUrl. Per concept #1379 only active published APIs are"
              + " discoverable: a DRAFT/READY dataset returns an empty list. Respects the caller's"
              + " X-Allowed-Scope-Ids; unknown or out-of-scope datasets return 404.")
  @ApiResponse(responseCode = "200", description = "Named APIs returned successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Dataset not found or out of scope",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<List<NamedApiOutputDTO>> getNamedApis(@PathVariable UUID id) {
    Specification<DataSet> scopedById =
        applyScopeFilter(ScopeFilteringSpecification.baseEntityById(Set.of(id)));
    DataSet dataSet =
        dataSetService
            .findOne(scopedById)
            .orElseThrow(() -> new ResourceNotFoundException("DataSet", id));
    // Only a published (AVAILABLE) dataset exposes active named APIs for consumer discovery
    // (concept
    // #1379). A DRAFT/READY dataset has no public API surface yet, so return an empty list rather
    // than leaking not-yet-active (or about-to-change) draft routes.
    List<NamedApiOutputDTO> namedApis =
        dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE
            ? dataSetAssembler.toOutput(dataSet).getNamedApis()
            : List.of();
    return ResponseEntity.ok(namedApis);
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
          "Validates the dataset's pipeline configuration and transitions status from DRAFT to READY.")
  public ResponseEntity<DataSetOutputDTO> stage(@PathVariable UUID id) {
    DataSet ready = dataSetService.stage(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(ready);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/unstage")
  @Operation(
      operationId = "unstageDataSet",
      summary = "Unstage a dataset",
      description = "Reverts the dataset from READY to DRAFT.")
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
