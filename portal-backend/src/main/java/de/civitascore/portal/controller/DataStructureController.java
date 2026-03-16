package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.assembler.DataStructureAssembler;
import de.civitascore.portal.repository.specification.DataStructureSpec;
import de.civitascore.portal.service.DataStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datastructures")
@RequiredArgsConstructor
@Tag(name = "Data Structures", description = "Data structure management endpoints")
public class DataStructureController
    extends BaseDataEntityController<
        DataStructureInputDTO, DataStructureOutputDTO, DataStructure, DataStructureSpec> {

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
        schema = @Schema(type = "string", example = "structure"))
  })
  @Override
  public ResponseEntity<Page<DataStructureOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataStructureSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  protected DataStructureService getService() {
    return dataStructureService;
  }

  @Override
  protected DataStructureAssembler getAssembler() {
    return dataStructureAssembler;
  }

  @Override
  public ScopeType getScopeType() {
    return ScopeType.DATASTRUCTURE;
  }

  @PutMapping("/{dataStructureId}/published/meta")
  @Operation(
      operationId = "updateDataStructurePublishedMeta",
      summary = "Update metadata of a published data structure",
      description =
          "Updates only the metadata (name, description) of a published data structure (AVAILABLE status). Cannot modify status or createdFromDataSource. For DRAFT data structures, use PUT /datastructures/{dataStructureId} instead.")
  public ResponseEntity<DataStructureOutputDTO> updatePublishedMeta(
      @PathVariable UUID dataStructureId, @Valid @RequestBody DataStructureInputDTO input) {
    DataStructure updated = dataStructureService.updatePublishedMeta(dataStructureId, input);
    DataStructureOutputDTO output = dataStructureAssembler.toOutput(updated);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{dataStructureId}/publish")
  @Operation(
      operationId = "publishDataStructure",
      summary = "Publish a data structure",
      description =
          "Publishes a data structure by setting status to AVAILABLE. Requires at least one published DataStructureVersion.")
  public ResponseEntity<DataStructureOutputDTO> publishDataStructure(
      @PathVariable UUID dataStructureId) {
    DataStructure published = dataStructureService.publish(dataStructureId);
    DataStructureOutputDTO output = dataStructureAssembler.toOutput(published);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{dataStructureId}/unpublish")
  @Operation(
      operationId = "unpublishDataStructure",
      summary = "Unpublish a data structure",
      description = "Unpublishes a data structure by setting status back to DRAFT. Always allowed.")
  public ResponseEntity<DataStructureOutputDTO> unpublishDataStructure(
      @PathVariable UUID dataStructureId) {
    DataStructure unpublished = dataStructureService.unpublish(dataStructureId);
    DataStructureOutputDTO output = dataStructureAssembler.toOutput(unpublished);
    return ResponseEntity.ok(output);
  }
}
