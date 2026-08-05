package de.civitascore.portal.controller;

import de.civitascore.portal.model.input.DataSetImportInputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO;
import de.civitascore.portal.service.DataSetImportService;
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
 * Imports a self-contained dataset bundle in a single call, for API clients that install use cases
 * programmatically. Lives under the flat {@code /imports} resource next to the single-structure
 * import: one path per importable artifact type, one gateway permission each.
 */
@RestController
@RequestMapping("/imports")
@RequiredArgsConstructor
@Tag(
    name = "Data Set Import",
    description =
        "One-call import of a dataset bundle: shell, data structures and data sources"
            + " (mappings/pipelines/sinks follow in later increments)")
public class DataSetImportController {

  private final DataSetImportService dataSetImportService;

  /**
   * Imports a dataset bundle: structures (create or reuse by URN identity), sources referencing
   * them, and the dataset shell — atomically.
   *
   * @param input the validated bundle
   * @return an import summary with HTTP 201 and a Location header pointing to the dataset
   */
  @Operation(
      summary = "Import a dataset bundle (shell + data structures + data sources) in one call",
      description =
          "Creates everything in DRAFT within one transaction. Contained data structures resolve"
              + " by URN identity: unknown → created, installed with identical content → reused,"
              + " installed with different content → 409. Releasing remains a separate step.")
  @ApiResponse(responseCode = "201", description = "Bundle imported; summary returned")
  @ApiResponse(
      responseCode = "400",
      description =
          "Invalid bundle (unsupported parts, unresolvable structure reference, invalid"
              + " contained artifact); nothing is created",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "A contained data structure's identity is already installed with different content;"
              + " nothing is created",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/datasets")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<DataSetImportOutputDTO> importDataSet(
      @Valid @RequestBody DataSetImportInputDTO input) {
    DataSetImportOutputDTO output = dataSetImportService.importDataSet(input);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/datasets/{id}")
            .buildAndExpand(output.getDataSetId())
            .toUri();
    return ResponseEntity.created(location).body(output);
  }
}
