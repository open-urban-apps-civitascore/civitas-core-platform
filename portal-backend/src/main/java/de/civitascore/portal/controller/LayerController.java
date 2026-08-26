package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.output.LayerOutputDTO;
import de.civitascore.portal.model.output.assembler.LayerAssembler;
import de.civitascore.portal.repository.specification.LayerSpec;
import de.civitascore.portal.service.LayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.MethodNotAllowedException;
import tools.jackson.databind.JsonNode;

/** REST controller for managing Layer resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/layers")
@RequiredArgsConstructor
@Tag(name = "Layers", description = "Layer management endpoints")
public class LayerController
    extends DataSetSubEntityController<LayerInputDTO, LayerOutputDTO, Layer, LayerSpec> {

  private final LayerService layerService;
  private final LayerAssembler layerAssembler;

  @Override
  protected LayerService getService() {
    return layerService;
  }

  @Override
  protected LayerAssembler getAssembler() {
    return layerAssembler;
  }

  @Override
  protected Class<Layer> getEntityClass() {
    return Layer.class;
  }

  @Override
  @Operation(operationId = "listLayers", summary = "List all Layers for a dataset")
  public ResponseEntity<Page<LayerOutputDTO>> getAll(
      @ParameterObject LayerSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  @Operation(operationId = "getLayer", summary = "Get Layer by ID")
  public ResponseEntity<LayerOutputDTO> getById(@PathVariable UUID id) {
    return super.getById(id);
  }

  @Override
  @Operation(operationId = "createLayer", summary = "Create a new Layer")
  public ResponseEntity<LayerOutputDTO> create(@Valid @RequestBody LayerInputDTO input) {
    return super.create(input);
  }

  @Override
  @Operation(operationId = "updateLayer", summary = "Replace a Layer")
  public ResponseEntity<LayerOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody LayerInputDTO input) {
    return super.update(id, input);
  }

  @Override
  @Operation(operationId = "deleteLayer", summary = "Delete a Layer")
  public void delete(@PathVariable UUID id) {
    super.delete(id);
  }

  /** PATCH is not supported for Layers. */
  @Override
  public ResponseEntity<LayerOutputDTO> patch(@PathVariable UUID id, @RequestBody JsonNode updates)
      throws IOException {
    throw new MethodNotAllowedException(HttpMethod.PATCH, Collections.emptySet());
  }
}
