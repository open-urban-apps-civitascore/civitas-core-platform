package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.model.output.assembler.DataStructureVersionAssembler;
import de.civitascore.portal.repository.specification.DataStructureVersionSpec;
import de.civitascore.portal.service.DataStructureVersionService;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.MethodNotAllowedException;
import tools.jackson.databind.JsonNode;

/**
 * REST controller for managing data structure version resources nested under a parent data
 * structure.
 *
 * <p>Versions support release/unrelease lifecycle and Model Atlas integration. Responses are
 * enriched with model data fetched from the external Model Atlas service.
 */
@RestController
@RequestMapping("/datastructures/{dataStructureId}/versions")
@RequiredArgsConstructor
@Tag(name = "Data Structure Versions", description = "Data structure version management endpoints")
public class DataStructureVersionController
    extends BaseController<
        DataStructureVersionInputDTO,
        DataStructureVersionOutputDTO,
        DataStructureVersion,
        DataStructureVersionSpec> {

  private final DataStructureVersionService dataStructureVersionService;
  private final DataStructureVersionAssembler dataStructureVersionAssembler;

  /**
   * Listing all versions is not supported on this endpoint; throws MethodNotAllowedException.
   *
   * @param spec the version search/filter specification (unused)
   * @param pageable pagination parameters (unused)
   * @return never returns normally
   * @throws org.springframework.web.server.MethodNotAllowedException always
   */
  @Override
  @Operation(hidden = true)
  public ResponseEntity<Page<DataStructureVersionOutputDTO>> getAll(
      @ParameterObject DataStructureVersionSpec spec, @ParameterObject Pageable pageable) {
    throw new MethodNotAllowedException(HttpMethod.GET, Collections.emptySet());
  }

  /** {@inheritDoc} */
  @Override
  protected DataStructureVersionService getService() {
    return dataStructureVersionService;
  }

  /** {@inheritDoc} */
  @Override
  protected DataStructureVersionAssembler getAssembler() {
    return dataStructureVersionAssembler;
  }

  /**
   * Creates a new data structure version and enriches the response with Model Atlas data.
   *
   * @param input the validated version input DTO
   * @return the created version output DTO with HTTP 201 status and a Location header
   */
  @Override
  @Operation(
      operationId = "createDataStructureVersion",
      summary = "Create a new data structure version")
  @ApiResponse(
      responseCode = "502",
      description = "Model Atlas upload failed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataStructureVersionOutputDTO> create(
      @Valid @RequestBody DataStructureVersionInputDTO input) {
    ResponseEntity<DataStructureVersionOutputDTO> response = super.create(input);
    DataStructureVersionOutputDTO output = response.getBody();
    if (output != null) {
      enrichWithModel(output);
    }
    return response;
  }

  /**
   * Retrieves a data structure version by ID and enriches it with Model Atlas data.
   *
   * @param id the UUID of the version to retrieve
   * @return the version output DTO enriched with model data, with HTTP 200 status
   */
  @Override
  @GetMapping("/{id}")
  @Operation(operationId = "getDataStructureVersion", summary = "Get data structure version by ID")
  public ResponseEntity<DataStructureVersionOutputDTO> getById(@PathVariable UUID id) {
    DataStructureVersion entity = getService().findByIdOrThrow(id);
    DataStructureVersionOutputDTO output = getAssembler().toOutput(entity);
    enrichWithModel(output);
    return ResponseEntity.ok(output);
  }

  /**
   * Applies a partial JSON-merge patch to a data structure version and enriches the response with
   * Model Atlas data.
   *
   * @param id the UUID of the version to patch
   * @param updates the JSON node containing the fields to update
   * @return the patched version output DTO with HTTP 200 status
   * @throws IOException if there is an error during JSON processing
   */
  @Override
  @Operation(
      operationId = "patchDataStructureVersion",
      summary = "Partially update a data structure version")
  @ApiResponse(
      responseCode = "502",
      description = "Model Atlas upload failed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataStructureVersionOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) throws IOException {
    ResponseEntity<DataStructureVersionOutputDTO> response = super.patch(id, updates);
    DataStructureVersionOutputDTO output = response.getBody();
    if (output != null) {
      enrichWithModel(output);
    }
    return response;
  }

  /**
   * Deletes a data structure version by its unique identifier.
   *
   * @param id the UUID of the version to delete
   */
  @Override
  @Operation(
      operationId = "deleteDataStructureVersion",
      summary = "Delete a data structure version")
  public void delete(@PathVariable UUID id) {
    super.delete(id);
  }

  /**
   * Injects the parent {@code dataStructureId} path variable into the input DTO before
   * create/update.
   *
   * @throws InvalidInputException if the path variable is missing or not a valid UUID
   */
  @Override
  protected DataStructureVersionInputDTO preProcessInput(DataStructureVersionInputDTO input) {
    Optional.ofNullable(extractPathVariables().get("dataStructureId"))
        .map(UUID::fromString)
        .ifPresentOrElse(
            input::setDataStructureId,
            () -> {
              throw new InvalidInputException(
                  "DataStructureVersion",
                  "dataStructureId",
                  "Missing or invalid dataStructureId in path variables");
            });
    return super.preProcessInput(input);
  }

  /**
   * Fully replaces a data structure version and enriches the response with Model Atlas data.
   *
   * @param id the UUID of the version to replace
   * @param input the validated version input DTO
   * @return the updated version output DTO with HTTP 200 status
   */
  @Override
  @Operation(
      operationId = "updateDataStructureVersion",
      summary = "Replace a data structure version")
  @ApiResponse(
      responseCode = "502",
      description = "Model Atlas upload failed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataStructureVersionOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody DataStructureVersionInputDTO input) {
    DataStructureVersionInputDTO preProcessedInput = preProcessInput(input);
    DataStructureVersion updated = getService().update(id, preProcessedInput);
    DataStructureVersionOutputDTO output = getAssembler().toOutput(updated);
    enrichWithModel(output);
    return ResponseEntity.ok(output);
  }

  /**
   * Updates the metadata of a released data structure version.
   *
   * @param dataStructureId the UUID of the parent data structure
   * @param versionId the UUID of the released version
   * @param input the validated version input DTO containing updated metadata
   * @return the updated version output DTO with HTTP 200 status
   */
  @PutMapping("/{versionId}/released/meta")
  @Operation(
      operationId = "updateDataStructureVersionReleasedMeta",
      summary = "Update metadata of a released data structure version",
      description =
          "Updates a released data structure version (AVAILABLE status). If the version is"
              + " not in use by any DataSource, all fields including model, modelAtlasUri,"
              + " version, and styles can be updated. If the version is in use, only description"
              + " and modelName can be changed. For DRAFT versions, use PUT"
              + " /datastructures/{dataStructureId}/versions/{versionId} instead.")
  @ApiResponse(responseCode = "200", description = "Released version metadata updated successfully")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid input (e.g. version is DRAFT)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Data structure version not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Conflict (e.g. unique constraint violation)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "502",
      description = "Model Atlas upload failed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataStructureVersionOutputDTO> updateReleasedMeta(
      @PathVariable UUID dataStructureId,
      @PathVariable UUID versionId,
      @Valid @RequestBody DataStructureVersionInputDTO input) {
    DataStructureVersionInputDTO preProcessedInput = preProcessInput(input);
    DataStructureVersion updated =
        dataStructureVersionService.updateReleasedMeta(versionId, preProcessedInput);
    DataStructureVersionOutputDTO output = dataStructureVersionAssembler.toOutput(updated);
    enrichWithModel(output);
    return ResponseEntity.ok(output);
  }

  /**
   * Releases a data structure version by setting its status to AVAILABLE.
   *
   * @param dataStructureId the UUID of the parent data structure
   * @param versionId the UUID of the version to release
   * @return the released version output DTO with HTTP 200 status
   */
  @PostMapping("/{versionId}/release")
  @Operation(
      operationId = "releaseDataStructureVersion",
      summary = "Release a data structure version",
      description =
          "Releases a data structure version by setting status to AVAILABLE. Requires"
              + " modelAtlasUri to be present.")
  @ApiResponse(responseCode = "200", description = "Data structure version released successfully")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid input (e.g. already released or missing modelAtlasUri)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Data structure version not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataStructureVersionOutputDTO> releaseDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    DataStructureVersion released = dataStructureVersionService.release(versionId);
    DataStructureVersionOutputDTO output = dataStructureVersionAssembler.toOutput(released);
    enrichWithModel(output);
    return ResponseEntity.ok(output);
  }

  /**
   * Unreleases a data structure version by reverting its status back to DRAFT.
   *
   * @param dataStructureId the UUID of the parent data structure
   * @param versionId the UUID of the version to unrelease
   * @return the unreleased version output DTO with HTTP 200 status
   */
  @PostMapping("/{versionId}/unrelease")
  @Operation(
      operationId = "unreleaseDataStructureVersion",
      summary = "Unrelease a data structure version",
      description =
          "Unreleases a data structure version by setting status back to DRAFT. Cannot unrelease"
              + " if this is the only published version of a published DataStructure - unpublish"
              + " the DataStructure first in that case.")
  @ApiResponse(responseCode = "200", description = "Data structure version unreleased successfully")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid input (e.g. already in DRAFT or only published version)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Data structure version not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Conflict (version is in use by a DataSource)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataStructureVersionOutputDTO> unreleaseDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    DataStructureVersion unreleased = dataStructureVersionService.unrelease(versionId);
    DataStructureVersionOutputDTO output = dataStructureVersionAssembler.toOutput(unreleased);
    enrichWithModel(output);
    return ResponseEntity.ok(output);
  }

  /**
   * Fetches the model definition from Model Atlas by the version's {@code modelAtlasUri} and sets
   * it on the output DTO.
   *
   * @param output the version output DTO to enrich
   */
  private void enrichWithModel(DataStructureVersionOutputDTO output) {
    getService().findModelByAtlasUri(output.getModelAtlasUri()).ifPresent(output::setModel);
  }
}
