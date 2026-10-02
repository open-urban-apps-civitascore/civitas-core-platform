package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureMetaInputDTO;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.assembler.DataStructureAssembler;
import de.civitascore.portal.repository.specification.DataStructureSpec;
import de.civitascore.portal.service.DataStructureService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing data structure resources, including release/unrelease lifecycle. */
@RestController
@RequestMapping("/datastructures")
@RequiredArgsConstructor
@Tag(name = "Data Structures", description = "Data structure management endpoints")
public class DataStructureController
    extends BaseDataEntityController<
        DataStructureInputDTO,
        DataStructureMetaInputDTO,
        DataStructureOutputDTO,
        DataStructure,
        DataStructureSpec> {

  private final DataStructureService dataStructureService;
  private final DataStructureAssembler dataStructureAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "My Data Structure")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Data structure description")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "structure")),
    @Parameter(
        name = "dataStructureStatus",
        description = "Filter by status (exact match).",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"DRAFT", "AVAILABLE"}))
  })
  /**
   * Retrieves a paginated list of data structures with optional filtering by name, description, or
   * free-text search.
   *
   * @param spec the data structure search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of data structure output DTOs with HTTP 200 status
   */
  @Override
  public ResponseEntity<Page<DataStructureOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataStructureSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /** {@inheritDoc} */
  @Override
  protected DataStructureService getService() {
    return dataStructureService;
  }

  /** {@inheritDoc} */
  @Override
  protected DataStructureAssembler getAssembler() {
    return dataStructureAssembler;
  }

  /** {@inheritDoc} */
  @Override
  public ScopeType getScopeType() {
    return ScopeType.DATASTRUCTURE;
  }

  @Override
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = @Content(schema = @Schema(implementation = DataStructureMetaInputDTO.class)))
  public ResponseEntity<DataStructureOutputDTO> updateReleasedMeta(
      @PathVariable UUID id, @RequestBody JsonNode updates) {
    return super.updateReleasedMeta(id, updates);
  }
}
