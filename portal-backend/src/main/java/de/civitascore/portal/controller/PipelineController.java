package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.model.output.assembler.PipelineAssembler;
import de.civitascore.portal.repository.specification.PipelineSpec;
import de.civitascore.portal.service.PipelineService;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
  public ResponseEntity<PipelineOutputDTO> getById(@PathVariable UUID id) {
    UUID dataSetId = extractDataSetId();
    Pipeline pipeline = pipelineService.findByIdAndDataSetOrThrow(id, dataSetId);
    return ResponseEntity.ok(pipelineAssembler.toOutput(pipeline));
  }

  @Override
  public void delete(@PathVariable UUID id) {
    UUID dataSetId = extractDataSetId();
    pipelineService.findByIdAndDataSetOrThrow(id, dataSetId);
    super.delete(id);
  }

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

  private UUID extractDataSetId() {
    return Optional.ofNullable(extractPathVariables().get("dataSetId"))
        .map(UUID::fromString)
        .orElseThrow(
            () ->
                new InvalidInputException(
                    "Pipeline", "dataSetId", "Missing or invalid dataSetId in path variables"));
  }
}
