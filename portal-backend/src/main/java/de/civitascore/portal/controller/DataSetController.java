package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSetMetaInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.NamedApiOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSetAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.DataSetSpec;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.service.DataSetService;
import de.civitascore.portal.util.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
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

/** REST controller for managing dataset resources, including release lifecycle operations. */
@RestController
@RequestMapping("/datasets")
@RequiredArgsConstructor
@Tag(name = "Datasets", description = "Dataset management endpoints")
public class DataSetController {

  private final DataSetService dataSetService;
  private final DataSetAssembler dataSetAssembler;
  private final JsonMergePatch jsonMergePatch;
  private final ScopeAssignments scopeAssignments;

  @GetMapping
  @Operation(summary = "List all datasets")
  public Page<DataSetOutputDTO> listDataSets(
      @ParameterObject DataSetSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return dataSetAssembler.toOutput(dataSetService.findAll(spec, pageable));
  }

  @GetMapping("/{dataSetId}")
  @Operation(summary = "Get a dataset by ID")
  @Problems(ProblemType.NOT_FOUND)
  public DataSetOutputDTO getDataSet(@PathVariable UUID dataSetId) {
    return dataSetAssembler.toOutput(dataSetService.findByIdOrThrow(dataSetId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new dataset")
  @Problems({ProblemType.INVALID_INPUT, ProblemType.NOT_FOUND, ProblemType.REGISTRY_UNAVAILABLE})
  public ResponseEntity<DataSetOutputDTO> createDataSet(@Valid @RequestBody DataSetInputDTO input) {
    DataSet created = dataSetService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(dataSetAssembler.toOutput(created));
  }

  @PutMapping("/{dataSetId}")
  @Operation(
      summary = "Update a DRAFT dataset",
      description =
          "Updates a dataset in DRAFT status. For released datasets (READY or AVAILABLE), use PATCH"
              + " /datasets/{dataSetId}/released/meta instead.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSetOutputDTO updateDataSet(
      @PathVariable UUID dataSetId, @Valid @RequestBody DataSetInputDTO input) {
    return dataSetAssembler.toOutput(dataSetService.update(dataSetId, input));
  }

  @PatchMapping("/{dataSetId}")
  @Operation(
      summary = "Partially update a dataset",
      description = JsonMergePatch.OPERATION_DESCRIPTION)
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSetOutputDTO patchDataSet(
      @PathVariable UUID dataSetId, @RequestBody JsonNode updates) {
    DataSetInputDTO current = dataSetAssembler.toInput(dataSetService.findByIdOrThrow(dataSetId));
    DataSetInputDTO patched = jsonMergePatch.apply(current, updates, dataSetId);
    return dataSetAssembler.toOutput(dataSetService.update(dataSetId, patched));
  }

  @DeleteMapping("/{dataSetId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a dataset",
      description =
          "Deletes a dataset. A never-provisioned dataset is removed immediately. A dataset that"
              + " still holds a provisioned sink is torn down asynchronously via a DELETE saga and"
              + " removed once the saga completes. An AVAILABLE dataset cannot be deleted directly"
              + " — unrelease it first (POST /datasets/{dataSetId}/unrelease).")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.RESOURCE_IN_USE,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public void deleteDataSet(@PathVariable UUID dataSetId) {
    dataSetService.deleteById(dataSetId);
  }

  @GetMapping("/{dataSetId}/apis")
  @Operation(
      summary = "List a dataset's published named APIs",
      description =
          "Returns the named APIs of a PUBLISHED dataset (lifecycle status AVAILABLE), including the"
              + " server-built previewUrl. Only active published APIs are discoverable: a"
              + " DRAFT/READY dataset returns an empty list. Respects the caller's"
              + " X-Allowed-Scope-Ids; unknown or out-of-scope datasets return 404.")
  @Problems(ProblemType.NOT_FOUND)
  public List<NamedApiOutputDTO> getDataSetApis(@PathVariable UUID dataSetId) {
    DataSet dataSet =
        dataSetService
            .findOne(ScopeFilteringSpecification.baseEntityById(Set.of(dataSetId)))
            .orElseThrow(() -> new ResourceNotFoundException("DataSet", dataSetId));
    // Draft edits must not change the public API surface before the dataset is published.
    return dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE
        ? dataSetAssembler.toOutput(dataSet).getNamedApis()
        : List.of();
  }

  @GetMapping("/{dataSetId}/assignments")
  @Operation(
      summary = "List the assignments of a dataset",
      description = "Returns all role assignments scoped to this dataset.")
  @Problems(ProblemType.NOT_FOUND)
  public List<AssignmentOutputDTO> getDataSetAssignments(@PathVariable UUID dataSetId) {
    dataSetService.findByIdOrThrow(dataSetId);
    return scopeAssignments.of(ScopeType.DATASET, dataSetId);
  }

  @PostMapping("/{dataSetId}/stage")
  @Operation(
      summary = "Stage a dataset",
      description =
          "Validates the dataset's pipeline configuration, then transitions status from DRAFT to"
              + " READY. The artifacts participating in its flows may still be DRAFT; release"
              + " validates them.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.DATASOURCE_SCOPE_VIOLATION})
  public DataSetOutputDTO stageDataSet(@PathVariable UUID dataSetId) {
    return dataSetAssembler.toOutput(dataSetService.stage(dataSetId));
  }

  @PostMapping("/{dataSetId}/unstage")
  @Operation(
      summary = "Unstage a dataset",
      description = "Reverts the dataset from READY to DRAFT.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.SAGA_IN_FLIGHT})
  public DataSetOutputDTO unstageDataSet(@PathVariable UUID dataSetId) {
    return dataSetAssembler.toOutput(dataSetService.unstage(dataSetId));
  }

  @PostMapping("/{dataSetId}/release")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "Release a dataset",
      description =
          "Transitions the dataset from READY to AVAILABLE. The release is accepted; its"
              + " infrastructure is provisioned asynchronously.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.PIPELINE_CLOSURE_INVALID,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSetOutputDTO releaseDataSet(@PathVariable UUID dataSetId) {
    return dataSetAssembler.toOutput(dataSetService.release(dataSetId));
  }

  @PostMapping("/{dataSetId}/unrelease")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "Unrelease a dataset",
      description =
          "Transitions the dataset from AVAILABLE back to READY. The unrelease is accepted; its"
              + " routes and pipelines are torn down asynchronously.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.SAGA_IN_FLIGHT})
  public DataSetOutputDTO unreleaseDataSet(@PathVariable UUID dataSetId) {
    return dataSetAssembler.toOutput(dataSetService.unrelease(dataSetId));
  }

  @PatchMapping("/{dataSetId}/released/meta")
  @Operation(
      summary = "Update metadata of a released dataset",
      description =
          "Applies a JSON merge patch to the metadata of a released dataset. An omitted field"
              + " keeps its value. A field that is not metadata answers 400, and so does null for"
              + " assignments. Only works on datasets that are not in DRAFT status.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = @Content(schema = @Schema(implementation = DataSetMetaInputDTO.class)))
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSetOutputDTO updateReleasedDataSetMeta(
      @PathVariable UUID dataSetId, @RequestBody JsonNode updates) {
    return dataSetAssembler.toOutput(
        dataSetService.updateReleasedMeta(dataSetId, patchMeta(dataSetId, updates)));
  }

  @PatchMapping("/{dataSetId}/ready/meta")
  @Operation(
      summary = "Update READY dataset metadata",
      description =
          "Applies a JSON merge patch to the metadata of a dataset in READY status, requiring only"
              + " DATASET_UPDATE — unlike PATCH /datasets/{dataSetId}/released/meta, which also"
              + " requires DATASET_RELEASE. An omitted field keeps its value. Rejects DRAFT and"
              + " AVAILABLE datasets. A field that is not metadata, such as namedApis, answers 400"
              + " — unrelease the dataset and edit it in DRAFT.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = @Content(schema = @Schema(implementation = DataSetMetaInputDTO.class)))
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.DATASOURCE_SCOPE_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSetOutputDTO updateReadyDataSetMeta(
      @PathVariable UUID dataSetId, @RequestBody JsonNode updates) {
    return dataSetAssembler.toOutput(
        dataSetService.updateReadyMeta(dataSetId, patchMeta(dataSetId, updates)));
  }

  private DataSetMetaInputDTO patchMeta(UUID dataSetId, JsonNode updates) {
    DataSetMetaInputDTO current =
        dataSetService.toMetaInput(dataSetService.findByIdOrThrow(dataSetId));
    return jsonMergePatch.apply(current, updates, dataSetId);
  }
}
