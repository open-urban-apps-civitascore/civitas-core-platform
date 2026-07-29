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
 * <p>Deliberately mapped under the existing {@code /datastructures} resource so the import shares
 * that resource's gateway routing and authorization surface rather than introducing a new one.
 */
@RestController
@RequestMapping("/datastructures")
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
      description = "Invalid input (e.g. model is not a valid JSON Schema); nothing is created",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/import")
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
