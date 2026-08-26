package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.model.output.assembler.PipelineAssembler;
import de.civitascore.portal.repository.specification.PipelineSpec;
import de.civitascore.portal.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing pipeline resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/pipelines")
@RequiredArgsConstructor
@Tag(name = "Pipelines", description = "Pipeline management endpoints")
public class PipelineController
    extends DataSetSubEntityController<
        PipelineInputDTO, PipelineOutputDTO, Pipeline, PipelineSpec> {

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
  @Operation(operationId = "listPipelines", summary = "List all pipelines")
  @Override
  public ResponseEntity<Page<PipelineOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") PipelineSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  protected PipelineService getService() {
    return pipelineService;
  }

  @Override
  protected PipelineAssembler getAssembler() {
    return pipelineAssembler;
  }

  @Override
  protected Class<Pipeline> getEntityClass() {
    return Pipeline.class;
  }

  @Override
  @Operation(operationId = "getPipeline", summary = "Get pipeline by ID")
  public ResponseEntity<PipelineOutputDTO> getById(@PathVariable UUID id) {
    return super.getById(id);
  }

  @Override
  @Operation(operationId = "createPipeline", summary = "Create a new pipeline")
  public ResponseEntity<PipelineOutputDTO> create(@Valid @RequestBody PipelineInputDTO input) {
    return super.create(input);
  }

  @Override
  @Operation(operationId = "updatePipeline", summary = "Replace a pipeline")
  public ResponseEntity<PipelineOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody PipelineInputDTO input) {
    return super.update(id, input);
  }

  @Override
  @Operation(operationId = "patchPipeline", summary = "Partially update a pipeline")
  public ResponseEntity<PipelineOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) throws IOException {
    return super.patch(id, updates);
  }

  @Override
  @Operation(operationId = "deletePipeline", summary = "Delete a pipeline")
  public void delete(@PathVariable UUID id) {
    super.delete(id);
  }
}
