package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.model.output.assembler.PipelineAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.PipelineSpec;
import de.civitascore.portal.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for Pipeline resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/pipelines")
@RequiredArgsConstructor
@Tag(name = "Pipelines", description = "Pipeline management endpoints")
public class PipelineController {

  private final PipelineService pipelineService;
  private final PipelineAssembler pipelineAssembler;
  private final JsonMergePatch jsonMergePatch;

  @GetMapping
  @Operation(summary = "List all pipelines of a dataset")
  public Page<PipelineOutputDTO> listPipelines(
      // Typed so that a malformed id answers 400 before PipelineSpec reads the path variable.
      @PathVariable UUID dataSetId,
      @ParameterObject PipelineSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return pipelineAssembler.toOutput(pipelineService.findAll(spec, pageable));
  }

  @GetMapping("/{pipelineId}")
  @Operation(summary = "Get a pipeline by ID")
  @Problems(ProblemType.NOT_FOUND)
  public PipelineOutputDTO getPipeline(
      @PathVariable UUID dataSetId, @PathVariable UUID pipelineId) {
    return pipelineAssembler.toOutput(requireOwned(dataSetId, pipelineId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new pipeline")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public ResponseEntity<PipelineOutputDTO> createPipeline(
      @PathVariable UUID dataSetId, @Valid @RequestBody PipelineInputDTO input) {
    input.setDataSetId(dataSetId);
    Pipeline created = pipelineService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(pipelineAssembler.toOutput(created));
  }

  @PutMapping("/{pipelineId}")
  @Operation(
      summary = "Replace a pipeline",
      description =
          "A DataSink that loses its last Pipeline through this update is removed with its Layers,"
              + " and with the APIs it is the last backing DataSink of.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REMOVAL_NOT_CONFIRMED,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public PipelineOutputDTO updatePipeline(
      @PathVariable UUID dataSetId,
      @PathVariable UUID pipelineId,
      @Valid @RequestBody PipelineInputDTO input,
      @Parameter(description = DataSinkController.CONFIRM_DATA_LOSS_DESCRIPTION)
          @RequestParam(defaultValue = "false")
          boolean confirmDataLoss) {
    requireOwned(dataSetId, pipelineId);
    input.setDataSetId(dataSetId);
    input.setConfirmDataLoss(confirmDataLoss);
    return pipelineAssembler.toOutput(pipelineService.update(pipelineId, input));
  }

  @PatchMapping("/{pipelineId}")
  @Operation(
      summary = "Partially update a pipeline",
      description =
          JsonMergePatch.OPERATION_DESCRIPTION
              + " A DataSink that loses its last Pipeline through this update is removed with its"
              + " Layers, and with the APIs it is the last backing DataSink of.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REMOVAL_NOT_CONFIRMED,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public PipelineOutputDTO patchPipeline(
      @PathVariable UUID dataSetId,
      @PathVariable UUID pipelineId,
      @RequestBody JsonNode updates,
      @Parameter(description = DataSinkController.CONFIRM_DATA_LOSS_DESCRIPTION)
          @RequestParam(defaultValue = "false")
          boolean confirmDataLoss) {
    PipelineInputDTO current = pipelineAssembler.toInput(requireOwned(dataSetId, pipelineId));
    PipelineInputDTO patched = jsonMergePatch.apply(current, updates, pipelineId);
    patched.setDataSetId(dataSetId);
    patched.setConfirmDataLoss(confirmDataLoss);
    return pipelineAssembler.toOutput(pipelineService.update(pipelineId, patched));
  }

  @DeleteMapping("/{pipelineId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a pipeline",
      description =
          "A DataSink whose last Pipeline this is is removed with its Layers, and with the APIs it"
              + " is the last backing DataSink of.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.RESOURCE_IN_USE,
    ProblemType.REMOVAL_NOT_CONFIRMED,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public void deletePipeline(
      @PathVariable UUID dataSetId,
      @PathVariable UUID pipelineId,
      @Parameter(description = DataSinkController.CONFIRM_DATA_LOSS_DESCRIPTION)
          @RequestParam(defaultValue = "false")
          boolean confirmDataLoss) {
    requireOwned(dataSetId, pipelineId);
    pipelineService.deleteById(pipelineId, confirmDataLoss);
  }

  private Pipeline requireOwned(UUID dataSetId, UUID pipelineId) {
    Pipeline pipeline = pipelineService.findByIdOrThrow(pipelineId);
    ParentOwnership.requireSameParent(
        dataSetId, pipeline.getDataSet().getId(), Pipeline.class, pipelineId);
    return pipeline;
  }
}
