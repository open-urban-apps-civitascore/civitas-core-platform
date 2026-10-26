package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.output.LayerOutputDTO;
import de.civitascore.portal.model.output.assembler.LayerAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.LayerSpec;
import de.civitascore.portal.service.LayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing Layer resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/layers")
@RequiredArgsConstructor
@Tag(name = "Layers", description = "Layer management endpoints")
public class LayerController {

  private final LayerService layerService;
  private final LayerAssembler layerAssembler;

  @GetMapping
  @Operation(summary = "List all Layers for a dataset")
  @ApiResponse(responseCode = "200", description = "Page of layers returned successfully")
  @Parameter(
      name = "id",
      description = "Filter by one or more IDs (comma-separated).",
      example = "1,2,3",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "1,2,3"))
  @Parameter(
      name = "createdAtFrom",
      description = "Filter by creation date greater than or equal to this value (ISO 8601).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", format = "date-time", example = "2024-01-01T00:00:00Z"))
  @Parameter(
      name = "createdAtTo",
      description = "Filter by creation date less than or equal to this value (ISO 8601).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", format = "date-time", example = "2024-12-31T23:59:59Z"))
  @Parameter(
      name = "modifiedAtFrom",
      description = "Filter by modification date greater than or equal to this value (ISO 8601).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", format = "date-time", example = "2024-01-01T00:00:00Z"))
  @Parameter(
      name = "modifiedAtTo",
      description = "Filter by modification date less than or equal to this value (ISO 8601).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", format = "date-time", example = "2024-12-31T23:59:59Z"))
  public Page<LayerOutputDTO> listLayers(
      // Typed so that a malformed id answers 400 before LayerSpec reads the path variable.
      @PathVariable UUID dataSetId,
      @ParameterObject LayerSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return layerAssembler.toOutput(layerService.findAll(spec, pageable));
  }

  @GetMapping("/{layerId}")
  @Operation(summary = "Get Layer by ID")
  @ApiResponse(responseCode = "200", description = "Layer returned successfully")
  @Problems(ProblemType.NOT_FOUND)
  public LayerOutputDTO getLayer(@PathVariable UUID dataSetId, @PathVariable UUID layerId) {
    return layerAssembler.toOutput(requireOwned(dataSetId, layerId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new Layer")
  @ApiResponse(responseCode = "201", description = "Layer created successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  public ResponseEntity<LayerOutputDTO> createLayer(
      @PathVariable UUID dataSetId, @Valid @RequestBody LayerInputDTO input) {
    input.setDataSetId(dataSetId);
    Layer created = layerService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(layerAssembler.toOutput(created));
  }

  @PutMapping("/{layerId}")
  @Operation(summary = "Replace a Layer")
  @ApiResponse(responseCode = "200", description = "Layer updated successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  public LayerOutputDTO updateLayer(
      @PathVariable UUID dataSetId,
      @PathVariable UUID layerId,
      @Valid @RequestBody LayerInputDTO input) {
    requireOwned(dataSetId, layerId);
    input.setDataSetId(dataSetId);
    return layerAssembler.toOutput(layerService.update(layerId, input));
  }

  @DeleteMapping("/{layerId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a Layer")
  @ApiResponse(responseCode = "204", description = "Layer deleted successfully")
  @Problems({ProblemType.NOT_FOUND, ProblemType.DATASET_NOT_EDITABLE, ProblemType.SAGA_IN_FLIGHT})
  public void deleteLayer(@PathVariable UUID dataSetId, @PathVariable UUID layerId) {
    requireOwned(dataSetId, layerId);
    layerService.deleteById(layerId);
  }

  private Layer requireOwned(UUID dataSetId, UUID layerId) {
    Layer layer = layerService.findByIdOrThrow(layerId);
    ParentOwnership.requireSameParent(dataSetId, layer.getDataSet().getId(), Layer.class, layerId);
    return layer;
  }
}
