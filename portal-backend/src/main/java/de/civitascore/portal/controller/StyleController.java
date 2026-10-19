package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.model.output.StyleOutputDTO;
import de.civitascore.portal.model.output.assembler.StyleAssembler;
import de.civitascore.portal.repository.specification.StyleSpec;
import de.civitascore.portal.service.StyleService;
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

/** REST controller for managing Style resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/styles")
@RequiredArgsConstructor
@Tag(name = "Styles", description = "Style management endpoints")
public class StyleController
    extends DataSetSubEntityController<StyleInputDTO, StyleOutputDTO, Style, StyleSpec> {

  private final StyleService styleService;
  private final StyleAssembler styleAssembler;

  @Override
  protected StyleService getService() {
    return styleService;
  }

  @Override
  protected StyleAssembler getAssembler() {
    return styleAssembler;
  }

  @Override
  protected Class<Style> getEntityClass() {
    return Style.class;
  }

  @Override
  @Operation(operationId = "listStyles", summary = "List all Styles for a dataset")
  public ResponseEntity<Page<StyleOutputDTO>> getAll(
      @ParameterObject StyleSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  @Operation(operationId = "getStyle", summary = "Get Style by ID")
  public ResponseEntity<StyleOutputDTO> getById(@PathVariable UUID id) {
    return super.getById(id);
  }

  @Override
  @Operation(operationId = "createStyle", summary = "Create a new Style")
  public ResponseEntity<StyleOutputDTO> create(@Valid @RequestBody StyleInputDTO input) {
    return super.create(input);
  }

  @Override
  @Operation(operationId = "updateStyle", summary = "Replace a Style")
  public ResponseEntity<StyleOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody StyleInputDTO input) {
    return super.update(id, input);
  }

  @Override
  @Operation(operationId = "deleteStyle", summary = "Delete a Style")
  public void delete(@PathVariable UUID id) {
    super.delete(id);
  }

  /** PATCH is not supported for Styles. */
  @Override
  public ResponseEntity<StyleOutputDTO> patch(@PathVariable UUID id, @RequestBody JsonNode updates)
      throws IOException {
    throw new MethodNotAllowedException(HttpMethod.PATCH, Collections.emptySet());
  }
}
