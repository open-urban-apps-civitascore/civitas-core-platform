package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.model.output.assembler.DataStructureVersionAssembler;
import de.civitascore.portal.service.DataStructureImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Imports a complete data structure in a single call, for API clients that provision structures
 * programmatically (the UI flow creates the shell and the version in two steps instead).
 *
 * <p>Mapped under a flat {@code /imports} resource with the artifact type as the sub-path. Each
 * importable artifact type gets its own path (this one today, datasets later), because the
 * gateway's policy engine authorizes by path+method and cannot look into request bodies — a
 * per-type path maps cleanly onto one required permission per type.
 */
@RestController
@RequestMapping("/imports")
@RequiredArgsConstructor
@Tag(
    name = "Data Structure Import",
    description = "One-call import of a data structure with its first version and model")
public class DataStructureImportController {

  private final DataStructureImportService dataStructureImportService;
  private final DataStructureVersionAssembler dataStructureVersionAssembler;

  /**
   * Imports a data structure: shell, first version and model content in one transaction.
   *
   * @param input the validated import input DTO
   * @return the created first version (carrying the parent structure summary and the model URN)
   *     with HTTP 201 and a Location header pointing to the version resource
   */
  @Operation(
      summary = "Import a data structure with its first version and model in one call",
      description =
          "Creates a DataStructure shell in DRAFT, its first DataStructureVersion in DRAFT, and"
              + " stores the model in the registry, atomically. Returns the created version;"
              + " its dataStructure field carries the new structure's id. Releasing remains a"
              + " separate step.")
  @ApiResponse(responseCode = "201", description = "Data structure and version created")
  @ApiResponse(
      responseCode = "400",
      description =
          "Invalid input (model has no ':datastructure:' URN as $id, or is not a valid JSON"
              + " Schema); nothing is created",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "A data structure for this model identity is already installed (a shell already pins"
              + " the model's logical URN); nothing is created",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/datastructures")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<DataStructureVersionOutputDTO> importDataStructure(
      @Valid @RequestBody DataStructureImportInputDTO input) {
    DataStructureVersion created = dataStructureImportService.importDataStructure(input);
    DataStructureVersionOutputDTO output = dataStructureVersionAssembler.toOutput(created);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/datastructures/{dataStructureId}/versions/{versionId}")
            .buildAndExpand(created.getDataStructure().getId(), created.getId())
            .toUri();
    return ResponseEntity.created(location).body(output);
  }
}
