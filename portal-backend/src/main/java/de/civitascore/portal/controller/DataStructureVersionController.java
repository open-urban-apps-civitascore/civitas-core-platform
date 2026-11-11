package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionMetaInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.model.output.assembler.DataStructureVersionAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.service.DataStructureVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * REST controller for managing data structure version resources nested under a parent data
 * structure.
 *
 * <p>Versions support a release/unrelease lifecycle. The model (a JSON Schema document) is
 * persisted directly on the version and returned as-is.
 */
@RestController
@RequestMapping("/datastructures/{dataStructureId}/versions")
@RequiredArgsConstructor
@Tag(name = "Data Structure Versions", description = "Data structure version management endpoints")
public class DataStructureVersionController {

  private final DataStructureVersionService dataStructureVersionService;
  private final DataStructureVersionAssembler dataStructureVersionAssembler;
  private final JsonMergePatch jsonMergePatch;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new data structure version")
  @ApiResponse(responseCode = "201", description = "Data structure version created successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.MODEL_REFERENCE_NOT_AVAILABLE,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public ResponseEntity<DataStructureVersionOutputDTO> createDataStructureVersion(
      @PathVariable UUID dataStructureId, @Valid @RequestBody DataStructureVersionInputDTO input) {
    input.setDataStructureId(dataStructureId);
    DataStructureVersion created = dataStructureVersionService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(dataStructureVersionAssembler.toOutput(created));
  }

  @GetMapping("/{versionId}")
  @Operation(summary = "Get data structure version by ID")
  @ApiResponse(responseCode = "200", description = "Data structure version returned successfully")
  @Problems(ProblemType.NOT_FOUND)
  public DataStructureVersionOutputDTO getDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    return dataStructureVersionAssembler.toOutput(requireOwned(dataStructureId, versionId));
  }

  @PutMapping("/{versionId}")
  @Operation(summary = "Replace a data structure version")
  @ApiResponse(responseCode = "200", description = "Data structure version updated successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.MODEL_REFERENCE_NOT_AVAILABLE,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataStructureVersionOutputDTO updateDataStructureVersion(
      @PathVariable UUID dataStructureId,
      @PathVariable UUID versionId,
      @Valid @RequestBody DataStructureVersionInputDTO input) {
    requireOwned(dataStructureId, versionId);
    input.setDataStructureId(dataStructureId);
    return dataStructureVersionAssembler.toOutput(
        dataStructureVersionService.update(versionId, input));
  }

  @PatchMapping("/{versionId}")
  @Operation(
      summary = "Partially update a data structure version",
      description = JsonMergePatch.OPERATION_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "Data structure version patched successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.MODEL_REFERENCE_NOT_AVAILABLE,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataStructureVersionOutputDTO patchDataStructureVersion(
      @PathVariable UUID dataStructureId,
      @PathVariable UUID versionId,
      @RequestBody JsonNode updates) {
    DataStructureVersionInputDTO current =
        dataStructureVersionAssembler.toInput(requireOwned(dataStructureId, versionId));
    DataStructureVersionInputDTO patched = jsonMergePatch.apply(current, updates, versionId);
    patched.setDataStructureId(dataStructureId);
    return dataStructureVersionAssembler.toOutput(
        dataStructureVersionService.update(versionId, patched));
  }

  @DeleteMapping("/{versionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a data structure version",
      description =
          "Deletes a DRAFT or AVAILABLE version that nothing uses, and its model with every"
              + " revision of it from the model registry. A deleted version number is not given"
              + " out again. The data structure stays, also without versions.")
  @ApiResponse(responseCode = "204", description = "Data structure version deleted successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.RESOURCE_IN_USE,
    ProblemType.FOREIGN_KEY_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public void deleteDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    requireOwned(dataStructureId, versionId);
    dataStructureVersionService.deleteById(versionId);
  }

  @PatchMapping("/{versionId}/released/meta")
  @Operation(
      operationId = "updateDataStructureVersionReleasedMeta",
      summary = "Update metadata of a released data structure version",
      description =
          "Applies a JSON merge patch to the description of a released data structure version"
              + " (AVAILABLE status). An omitted field keeps its value. The description is the only"
              + " field of a released version that can change; a request that contains any other"
              + " field answers 400. For DRAFT versions, use PATCH"
              + " /datastructures/{dataStructureId}/versions/{versionId} instead.")
  @ApiResponse(responseCode = "200", description = "Released version metadata updated successfully")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = @Content(schema = @Schema(implementation = DataStructureVersionMetaInputDTO.class)))
  @Problems(ProblemType.NOT_FOUND)
  public ResponseEntity<DataStructureVersionOutputDTO> updateDataStructureVersionReleasedMeta(
      @PathVariable UUID dataStructureId,
      @PathVariable UUID versionId,
      @RequestBody JsonNode updates) {
    DataStructureVersionMetaInputDTO current =
        dataStructureVersionService.toMetaInput(requireOwned(dataStructureId, versionId));
    DataStructureVersion updated =
        dataStructureVersionService.updateReleasedMeta(
            versionId, jsonMergePatch.apply(current, updates, versionId));
    return ResponseEntity.ok(dataStructureVersionAssembler.toOutput(updated));
  }

  @PostMapping("/{versionId}/release")
  @Operation(
      operationId = "releaseDataStructureVersion",
      summary = "Release a data structure version",
      description =
          "Releases a data structure version by setting status to AVAILABLE. Requires a model"
              + " (JSON schema) with exactly one root Element that reaches every other member.")
  @ApiResponse(responseCode = "200", description = "Data structure version released successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.MODEL_ROOT_MISSING,
    ProblemType.MODEL_MEMBER_UNREACHABLE,
    ProblemType.MODEL_MISSING,
    ProblemType.MODEL_REFERENCE_NOT_AVAILABLE,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public ResponseEntity<DataStructureVersionOutputDTO> releaseDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    requireOwned(dataStructureId, versionId);
    DataStructureVersion released = dataStructureVersionService.release(versionId);
    return ResponseEntity.ok(dataStructureVersionAssembler.toOutput(released));
  }

  @PostMapping("/{versionId}/unrelease")
  @Operation(
      operationId = "unreleaseDataStructureVersion",
      summary = "Unrelease a data structure version",
      description = "Unreleases a data structure version by setting status back to DRAFT.")
  @ApiResponse(responseCode = "200", description = "Data structure version unreleased successfully")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE})
  public ResponseEntity<DataStructureVersionOutputDTO> unreleaseDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    requireOwned(dataStructureId, versionId);
    DataStructureVersion unreleased = dataStructureVersionService.unrelease(versionId);
    return ResponseEntity.ok(dataStructureVersionAssembler.toOutput(unreleased));
  }

  @PostMapping("/{versionId}/duplicate")
  @Operation(
      operationId = "duplicateDataStructureVersion",
      summary = "Duplicate a data structure version",
      description =
          "Creates a new DRAFT version with the description, model name and imported structures"
              + " of the source version. The model registry copies the source's model with its"
              + " styles and references to the next major version after the highest existing one."
              + " A source without a model gives a copy without a model. Any version can be"
              + " duplicated.")
  @ApiResponse(responseCode = "201", description = "Data structure version duplicated successfully")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.MODEL_REFERENCE_NOT_AVAILABLE,
    ProblemType.MODEL_MISSING,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public ResponseEntity<DataStructureVersionOutputDTO> duplicateDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    requireOwned(dataStructureId, versionId);
    DataStructureVersion duplicated = dataStructureVersionService.duplicate(versionId);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/datastructures/{dataStructureId}/versions/{id}")
            .buildAndExpand(dataStructureId, duplicated.getId())
            .toUri();
    return ResponseEntity.created(location)
        .body(dataStructureVersionAssembler.toOutput(duplicated));
  }

  private DataStructureVersion requireOwned(UUID dataStructureId, UUID versionId) {
    DataStructureVersion version = dataStructureVersionService.findByIdOrThrow(versionId);
    ParentOwnership.requireSameParent(
        dataStructureId, version.getDataStructure().getId(), DataStructureVersion.class, versionId);
    return version;
  }
}
