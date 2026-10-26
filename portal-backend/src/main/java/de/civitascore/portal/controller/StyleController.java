package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.model.output.StyleOutputDTO;
import de.civitascore.portal.model.output.assembler.StyleAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.StyleSpec;
import de.civitascore.portal.service.StyleService;
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

/** REST controller for managing Style resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/styles")
@RequiredArgsConstructor
@Tag(name = "Styles", description = "Style management endpoints")
public class StyleController {

  private final StyleService styleService;
  private final StyleAssembler styleAssembler;

  @GetMapping
  @Operation(summary = "List all Styles for a dataset")
  @ApiResponse(responseCode = "200", description = "Page of styles returned successfully")
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
  public Page<StyleOutputDTO> listStyles(
      // Typed so that a malformed id answers 400 before StyleSpec reads the path variable.
      @PathVariable UUID dataSetId,
      @ParameterObject StyleSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return styleAssembler.toOutput(styleService.findAll(spec, pageable));
  }

  @GetMapping("/{styleId}")
  @Operation(summary = "Get Style by ID")
  @ApiResponse(responseCode = "200", description = "Style returned successfully")
  @Problems(ProblemType.NOT_FOUND)
  public StyleOutputDTO getStyle(@PathVariable UUID dataSetId, @PathVariable UUID styleId) {
    return styleAssembler.toOutput(requireOwned(dataSetId, styleId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new Style")
  @ApiResponse(responseCode = "201", description = "Style created successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  public ResponseEntity<StyleOutputDTO> createStyle(
      @PathVariable UUID dataSetId, @Valid @RequestBody StyleInputDTO input) {
    input.setDataSetId(dataSetId);
    Style created = styleService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(styleAssembler.toOutput(created));
  }

  @PutMapping("/{styleId}")
  @Operation(summary = "Replace a Style")
  @ApiResponse(responseCode = "200", description = "Style updated successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  public StyleOutputDTO updateStyle(
      @PathVariable UUID dataSetId,
      @PathVariable UUID styleId,
      @Valid @RequestBody StyleInputDTO input) {
    requireOwned(dataSetId, styleId);
    input.setDataSetId(dataSetId);
    return styleAssembler.toOutput(styleService.update(styleId, input));
  }

  @DeleteMapping("/{styleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a Style")
  @ApiResponse(responseCode = "204", description = "Style deleted successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.RESOURCE_IN_USE
  })
  public void deleteStyle(@PathVariable UUID dataSetId, @PathVariable UUID styleId) {
    requireOwned(dataSetId, styleId);
    styleService.deleteById(styleId);
  }

  private Style requireOwned(UUID dataSetId, UUID styleId) {
    Style style = styleService.findByIdOrThrow(styleId);
    ParentOwnership.requireSameParent(dataSetId, style.getDataSet().getId(), Style.class, styleId);
    return style;
  }
}
