package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.input.DataPoolInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.DataPoolOutputDTO;
import de.civitascore.portal.model.output.assembler.DataPoolAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.DataPoolSpec;
import de.civitascore.portal.service.DataPoolService;
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

/** REST controller for managing data pool resources. */
@RestController
@RequestMapping("/datapools")
@RequiredArgsConstructor
@Tag(name = "Data pools", description = "Data pool management endpoints")
public class DataPoolController {

  private final DataPoolService dataPoolService;
  private final DataPoolAssembler dataPoolAssembler;
  private final JsonMergePatch jsonMergePatch;
  private final ScopeAssignments scopeAssignments;

  @GetMapping
  @Operation(summary = "List all data pools")
  public Page<DataPoolOutputDTO> listDataPools(
      @ParameterObject DataPoolSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return dataPoolAssembler.toOutput(dataPoolService.findAll(spec, pageable));
  }

  @GetMapping("/{dataPoolId}")
  @Operation(summary = "Get a data pool by ID")
  @Problems(ProblemType.NOT_FOUND)
  public DataPoolOutputDTO getDataPool(@PathVariable UUID dataPoolId) {
    return dataPoolAssembler.toOutput(dataPoolService.findByIdOrThrow(dataPoolId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new data pool")
  @Problems({ProblemType.INVALID_INPUT, ProblemType.NOT_FOUND})
  public ResponseEntity<DataPoolOutputDTO> createDataPool(
      @Valid @RequestBody DataPoolInputDTO input) {
    DataPool created = dataPoolService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(dataPoolAssembler.toOutput(created));
  }

  @PutMapping("/{dataPoolId}")
  @Operation(summary = "Replace a data pool")
  @Problems(ProblemType.NOT_FOUND)
  public DataPoolOutputDTO updateDataPool(
      @PathVariable UUID dataPoolId, @Valid @RequestBody DataPoolInputDTO input) {
    return dataPoolAssembler.toOutput(dataPoolService.update(dataPoolId, input));
  }

  @PatchMapping("/{dataPoolId}")
  @Operation(
      summary = "Partially update a data pool",
      description = JsonMergePatch.OPERATION_DESCRIPTION)
  @Problems(ProblemType.NOT_FOUND)
  public DataPoolOutputDTO patchDataPool(
      @PathVariable UUID dataPoolId, @RequestBody JsonNode updates) {
    DataPoolInputDTO current =
        dataPoolAssembler.toInput(dataPoolService.findByIdOrThrow(dataPoolId));
    DataPoolInputDTO patched = jsonMergePatch.apply(current, updates, dataPoolId);
    return dataPoolAssembler.toOutput(dataPoolService.update(dataPoolId, patched));
  }

  @DeleteMapping("/{dataPoolId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a data pool",
      description =
          "Permanently deletes a data pool by its UUID. Fails with 409 Conflict while datasets are"
              + " assigned to it or data sources are scoped to it.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE})
  public void deleteDataPool(@PathVariable UUID dataPoolId) {
    dataPoolService.deleteById(dataPoolId);
  }

  @GetMapping("/{dataPoolId}/assignments")
  @Operation(
      summary = "List the assignments of a data pool",
      description = "Returns all role assignments scoped to this data pool.")
  @Problems(ProblemType.NOT_FOUND)
  public List<AssignmentOutputDTO> getDataPoolAssignments(@PathVariable UUID dataPoolId) {
    dataPoolService.findByIdOrThrow(dataPoolId);
    return scopeAssignments.of(ScopeType.DATAPOOL, dataPoolId);
  }
}
