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
  public Page<LayerOutputDTO> listLayers(
      @PathVariable UUID dataSetId,
      @ParameterObject LayerSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return layerAssembler.toOutput(layerService.findAll(dataSetId, spec, pageable));
  }

  @GetMapping("/{layerId}")
  @Operation(summary = "Get Layer by ID")
  @Problems(ProblemType.NOT_FOUND)
  public LayerOutputDTO getLayer(@PathVariable UUID dataSetId, @PathVariable UUID layerId) {
    return layerAssembler.toOutput(layerService.findOwned(dataSetId, layerId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new Layer")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  public ResponseEntity<LayerOutputDTO> createLayer(
      @PathVariable UUID dataSetId, @Valid @RequestBody LayerInputDTO input) {
    Layer created = layerService.create(dataSetId, input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(layerAssembler.toOutput(created));
  }

  @PutMapping("/{layerId}")
  @Operation(summary = "Replace a Layer")
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
    return layerAssembler.toOutput(layerService.update(dataSetId, layerId, input));
  }

  @DeleteMapping("/{layerId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a Layer")
  @Problems({ProblemType.NOT_FOUND, ProblemType.DATASET_NOT_EDITABLE, ProblemType.SAGA_IN_FLIGHT})
  public void deleteLayer(@PathVariable UUID dataSetId, @PathVariable UUID layerId) {
    layerService.delete(dataSetId, layerId);
  }
}
