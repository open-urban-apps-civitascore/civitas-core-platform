package de.civitascore.portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

  @Override
  @Operation(hidden = true)
  public ResponseEntity<Page<DataStructureVersionOutputDTO>> getAll(
      @ParameterObject DataStructureVersionSpec spec, @ParameterObject Pageable pageable) {
    throw new MethodNotAllowedException(HttpMethod.GET, Collections.emptySet());
  }

  @Override
  protected DataStructureVersionService getService() {
    return dataStructureVersionService;
  }

  @Override
  protected DataStructureVersionAssembler getAssembler() {
    return dataStructureVersionAssembler;
  }

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
      DataStructureVersion created = getService().findByIdOrThrow(output.getId());
      enrichWithModel(output, created);
    }
    return response;
  }

  @Override
  @GetMapping("/{id}")
  @Operation(operationId = "getDataStructureVersion", summary = "Get data structure version by ID")
  public ResponseEntity<DataStructureVersionOutputDTO> getById(@PathVariable UUID id) {
    DataStructureVersion entity = getService().findByIdOrThrow(id);
    DataStructureVersionOutputDTO output = getAssembler().toOutput(entity);
    enrichWithModel(output, entity);
    return ResponseEntity.ok(output);
  }

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
    if (output != null && !updates.has("model")) {
      DataStructureVersion entity = getService().findByIdOrThrow(output.getId());
      dataStructureVersionService
          .findModelForDataStructureVersion(entity)
          .ifPresent(output::setModel);
    }
    return response;
  }

  @Override
  @Operation(
      operationId = "deleteDataStructureVersion",
      summary = "Delete a data structure version")
  public void delete(@PathVariable UUID id) {
    super.delete(id);
  }

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
    enrichWithModel(output, updated);
    return ResponseEntity.ok(output);
  }

  @PutMapping("/{versionId}/published/meta")
  @Operation(
      operationId = "updateDataStructureVersionPublishedMeta",
      summary = "Update metadata of a published data structure version",
      description =
          "Updates a published data structure version (AVAILABLE status). If the version is"
              + " not in use by any DataSource, all fields including model, modelAtlasUri,"
              + " version, and styles can be updated. If the version is in use, only description"
              + " and modelName can be changed. For DRAFT versions, use PUT"
              + " /datastructures/{dataStructureId}/versions/{versionId} instead.")
  @ApiResponse(
      responseCode = "200",
      description = "Published version metadata updated successfully")
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
  public ResponseEntity<DataStructureVersionOutputDTO> updatePublishedMeta(
      @PathVariable UUID dataStructureId,
      @PathVariable UUID versionId,
      @Valid @RequestBody DataStructureVersionInputDTO input) {
    DataStructureVersionInputDTO preProcessedInput = preProcessInput(input);
    DataStructureVersion updated =
        dataStructureVersionService.updatePublishedMeta(versionId, preProcessedInput);
    DataStructureVersionOutputDTO output = dataStructureVersionAssembler.toOutput(updated);
    enrichWithModel(output, updated);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{versionId}/publish")
  @Operation(
      operationId = "publishDataStructureVersion",
      summary = "Publish a data structure version",
      description =
          "Publishes a data structure version by setting status to AVAILABLE. Requires"
              + " modelAtlasUri to be present.")
  @ApiResponse(responseCode = "200", description = "Data structure version published successfully")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid input (e.g. already published or missing modelAtlasUri)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Data structure version not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<DataStructureVersionOutputDTO> publishDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    DataStructureVersion published = dataStructureVersionService.publish(versionId);
    DataStructureVersionOutputDTO output = dataStructureVersionAssembler.toOutput(published);
    enrichWithModel(output, published);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{versionId}/unpublish")
  @Operation(
      operationId = "unpublishDataStructureVersion",
      summary = "Unpublish a data structure version",
      description =
          "Unpublishes a data structure version by setting status back to DRAFT. Cannot unpublish"
              + " if this is the only published version of a published DataStructure - unpublish"
              + " the DataStructure first in that case.")
  @ApiResponse(
      responseCode = "200",
      description = "Data structure version unpublished successfully")
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
  public ResponseEntity<DataStructureVersionOutputDTO> unpublishDataStructureVersion(
      @PathVariable UUID dataStructureId, @PathVariable UUID versionId) {
    DataStructureVersion unpublished = dataStructureVersionService.unpublish(versionId);
    DataStructureVersionOutputDTO output = dataStructureVersionAssembler.toOutput(unpublished);
    enrichWithModel(output, unpublished);
    return ResponseEntity.ok(output);
  }

  private void enrichWithModel(DataStructureVersionOutputDTO output, DataStructureVersion entity) {
    getService().findModelForDataStructureVersion(entity).ifPresent(output::setModel);
  }
}
