package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.assembler.DataStructureAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.DataStructureSpec;
import de.civitascore.portal.service.DataStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing data structure resources. */
@RestController
@RequestMapping("/datastructures")
@RequiredArgsConstructor
@Tag(name = "Data structures", description = "Data structure management endpoints")
public class DataStructureController {

  private final DataStructureService dataStructureService;
  private final DataStructureAssembler dataStructureAssembler;
  private final JsonMergePatch jsonMergePatch;
  private final ScopeFilter scopeFilter;
  private final ScopeAssignments scopeAssignments;

  @GetMapping
  @Operation(summary = "List all data structures")
  public Page<DataStructureOutputDTO> listDataStructures(
      @ParameterObject DataStructureSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return dataStructureAssembler.toOutput(
        dataStructureService.findAll(scopeFilter.restrict(spec), pageable));
  }

  @GetMapping("/{dataStructureId}")
  @Operation(summary = "Get a data structure by ID")
  @Problems(ProblemType.NOT_FOUND)
  public DataStructureOutputDTO getDataStructure(@PathVariable UUID dataStructureId) {
    return dataStructureAssembler.toOutput(dataStructureService.findByIdOrThrow(dataStructureId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new data structure")
  @Problems({ProblemType.INVALID_INPUT, ProblemType.NOT_FOUND})
  public ResponseEntity<DataStructureOutputDTO> createDataStructure(
      @Valid @RequestBody DataStructureInputDTO input) {
    DataStructure created = dataStructureService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(dataStructureAssembler.toOutput(created));
  }

  @PutMapping("/{dataStructureId}")
  @Operation(summary = "Replace a data structure")
  @Problems(ProblemType.NOT_FOUND)
  public DataStructureOutputDTO updateDataStructure(
      @PathVariable UUID dataStructureId, @Valid @RequestBody DataStructureInputDTO input) {
    return dataStructureAssembler.toOutput(dataStructureService.update(dataStructureId, input));
  }

  @PatchMapping("/{dataStructureId}")
  @Operation(
      summary = "Partially update a data structure",
      description = JsonMergePatch.OPERATION_DESCRIPTION)
  @Problems(ProblemType.NOT_FOUND)
  public DataStructureOutputDTO patchDataStructure(
      @PathVariable UUID dataStructureId, @RequestBody JsonNode updates) {
    DataStructureInputDTO current =
        dataStructureAssembler.toInput(dataStructureService.findByIdOrThrow(dataStructureId));
    DataStructureInputDTO patched = jsonMergePatch.apply(current, updates, dataStructureId);
    return dataStructureAssembler.toOutput(dataStructureService.update(dataStructureId, patched));
  }

  @DeleteMapping("/{dataStructureId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a data structure")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE, ProblemType.REGISTRY_UNAVAILABLE})
  public void deleteDataStructure(@PathVariable UUID dataStructureId) {
    dataStructureService.deleteById(dataStructureId);
  }

  @GetMapping("/{dataStructureId}/assignments")
  @Operation(
      summary = "List the assignments of a data structure",
      description = "Returns all role assignments scoped to this data structure.")
  @Problems(ProblemType.NOT_FOUND)
  public List<AssignmentOutputDTO> getDataStructureAssignments(@PathVariable UUID dataStructureId) {
    dataStructureService.findByIdOrThrow(dataStructureId);
    return scopeAssignments.of(ScopeType.DATASTRUCTURE, dataStructureId);
  }
}
