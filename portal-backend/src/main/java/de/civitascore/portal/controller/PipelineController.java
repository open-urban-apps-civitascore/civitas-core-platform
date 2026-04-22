package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.model.output.assembler.PipelineAssembler;
import de.civitascore.portal.repository.specification.PipelineSpec;
import de.civitascore.portal.service.PipelineService;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing pipeline resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/pipelines")
@RequiredArgsConstructor
@Tag(name = "Pipelines", description = "Pipeline management endpoints")
public class PipelineController
    extends BaseController<PipelineInputDTO, PipelineOutputDTO, Pipeline, PipelineSpec> {

  private final PipelineService pipelineService;
  private final PipelineAssembler pipelineAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "traffic-pipeline")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Processing traffic data")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "traffic"))
  })
  /**
   * Retrieves a paginated list of pipelines for a dataset with optional filtering by name,
   * description, or free-text search.
   *
   * @param spec the pipeline search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of pipeline output DTOs with HTTP 200 status
   */
  @Operation(operationId = "listPipelines", summary = "List all pipelines")
  @Override
  public ResponseEntity<Page<PipelineOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") PipelineSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /** {@inheritDoc} */
  @Override
  protected PipelineService getService() {
    return pipelineService;
  }

  /** {@inheritDoc} */
  @Override
  protected PipelineAssembler getAssembler() {
    return pipelineAssembler;
  }

  /**
   * Retrieves a pipeline by ID, verifying it belongs to the parent dataset.
   *
   * @param id the UUID of the pipeline to retrieve
   * @return the pipeline output DTO with HTTP 200 status
   */
  @Override
  @Operation(operationId = "getPipeline", summary = "Get pipeline by ID")
  public ResponseEntity<PipelineOutputDTO> getById(@PathVariable UUID id) {
    UUID dataSetId = extractDataSetId();
    Pipeline pipeline = pipelineService.findByIdAndDataSetOrThrow(id, dataSetId);
    return ResponseEntity.ok(pipelineAssembler.toOutput(pipeline));
  }

  /**
   * Deletes a pipeline after verifying it belongs to the parent dataset.
   *
   * @param id the UUID of the pipeline to delete
   */
  @Override
  @Operation(operationId = "deletePipeline", summary = "Delete a pipeline")
  public void delete(@PathVariable UUID id) {
    UUID dataSetId = extractDataSetId();
    pipelineService.findByIdAndDataSetOrThrow(id, dataSetId);
    super.delete(id);
  }

  /**
   * Creates a new pipeline under the parent dataset.
   *
   * @param input the validated pipeline input DTO
   * @return the created pipeline output DTO with HTTP 201 status and a Location header
   */
  @Override
  @Operation(operationId = "createPipeline", summary = "Create a new pipeline")
  public ResponseEntity<PipelineOutputDTO> create(@Valid @RequestBody PipelineInputDTO input) {
    return super.create(input);
  }

  /**
   * Fully replaces an existing pipeline with the provided input.
   *
   * @param id the UUID of the pipeline to replace
   * @param input the validated pipeline input DTO
   * @return the updated pipeline output DTO with HTTP 200 status
   */
  @Override
  @Operation(operationId = "updatePipeline", summary = "Replace a pipeline")
  public ResponseEntity<PipelineOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody PipelineInputDTO input) {
    return super.update(id, input);
  }

  /**
   * Applies a partial JSON-merge patch to an existing pipeline.
   *
   * @param id the UUID of the pipeline to patch
   * @param updates the JSON node containing the fields to update
   * @return the patched pipeline output DTO with HTTP 200 status
   * @throws IOException if there is an error during JSON processing
   */
  @Override
  @Operation(operationId = "patchPipeline", summary = "Partially update a pipeline")
  public ResponseEntity<PipelineOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) throws IOException {
    return super.patch(id, updates);
  }

  /**
   * Injects the parent {@code dataSetId} path variable into the input DTO before create/update.
   *
   * @throws InvalidInputException if the path variable is missing or not a valid UUID
   */
  @Override
  protected PipelineInputDTO preProcessInput(PipelineInputDTO input) {
    Optional.ofNullable(extractPathVariables().get("dataSetId"))
        .map(UUID::fromString)
        .ifPresentOrElse(
            input::setDataSetId,
            () -> {
              throw new InvalidInputException(
                  "Pipeline", "dataSetId", "Missing or invalid dataSetId in path variables");
            });
    return super.preProcessInput(input);
  }

  /**
   * Extracts and parses the {@code dataSetId} path variable from the current request.
   *
   * @return the dataset UUID
   * @throws InvalidInputException if the path variable is missing or not a valid UUID
   */
  private UUID extractDataSetId() {
    return Optional.ofNullable(extractPathVariables().get("dataSetId"))
        .map(UUID::fromString)
        .orElseThrow(
            () ->
                new InvalidInputException(
                    "Pipeline", "dataSetId", "Missing or invalid dataSetId in path variables"));
  }
}
