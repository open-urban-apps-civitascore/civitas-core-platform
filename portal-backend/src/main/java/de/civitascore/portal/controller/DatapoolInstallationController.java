package de.civitascore.portal.controller;

import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.assembler.InstallationAssembler;
import de.civitascore.portal.service.InstallationService;
import de.civitascore.portal.service.UninstallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the installations of one Data pool. The operations are the same as in {@link
 * InstallationController}, under the path of the Data pool. OPA checks the permissions on the Data
 * pool of the path, so a grant on the Data pool is sufficient. This controller makes sure that each
 * installation that it reads or uninstalls is in that Data pool.
 */
@RestController
@RequestMapping(
    value = "/datapools/{datapoolId}/installations",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(
    name = "Installations",
    description = "Install and uninstall packages, and read the record of each install")
public class DatapoolInstallationController {

  private final InstallationService installationService;
  private final UninstallService uninstallService;
  private final InstallationAssembler installationAssembler;

  /**
   * Installs a package in a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param input the package to install
   * @return the record of the install with HTTP 201 status
   */
  @Operation(
      operationId = "installInDatapool",
      summary = "Install a package in a Data pool",
      description =
          "Installs the package as POST /installations does, in the Data pool of the path. The"
              + " request can leave out datapoolId. The record keeps the Data pool also for a"
              + " package without Data sources and without a Dataset.")
  @ApiResponse(responseCode = "201", description = "The package is installed")
  @ApiResponse(
      responseCode = "400",
      description =
          "The request is not valid, the request names another Data pool, or the platform refuses"
              + " a member",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Data pool not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The package is already installed in the Data pool",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<InstallationOutputDTO> install(
      @PathVariable UUID datapoolId, @Valid @RequestBody InstallationInputDTO input) {
    InstallationOutputDTO output =
        installationAssembler.toOutput(installationService.installInDatapool(datapoolId, input));
    return ResponseEntity.status(HttpStatus.CREATED).body(output);
  }

  /**
   * Returns a page of the installations of a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param pageable pagination and sorting parameters
   * @return a page of installations with HTTP 200 status
   */
  @Operation(
      operationId = "listDatapoolInstallations",
      summary = "List the installations of a Data pool",
      description =
          "Returns a page of the installations of the Data pool, the active and the uninstalled"
              + " ones. Each installation contains its lines.")
  @ApiResponse(responseCode = "200", description = "Page of installations returned successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Data pool not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping
  public ResponseEntity<Page<InstallationOutputDTO>> getAll(
      @PathVariable UUID datapoolId,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        installationAssembler.toOutput(
            installationService.findAllInDatapool(datapoolId, pageable)));
  }

  /**
   * Returns one installation of a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param id the id of the installation
   * @return the installation with HTTP 200 status
   */
  @Operation(
      operationId = "getDatapoolInstallation",
      summary = "Get an installation of a Data pool",
      description = "Returns one installation of the Data pool with its lines.")
  @ApiResponse(responseCode = "200", description = "Installation returned successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Installation not found in the Data pool",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}")
  public ResponseEntity<InstallationOutputDTO> getById(
      @PathVariable UUID datapoolId, @PathVariable UUID id) {
    return ResponseEntity.ok(
        installationAssembler.toOutput(installationService.findInDatapoolOrThrow(datapoolId, id)));
  }

  /**
   * Uninstalls an installation of a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param id the id of the installation
   * @return HTTP 204 status
   */
  @Operation(
      operationId = "uninstallDatapoolInstallation",
      summary = "Uninstall an installation of a Data pool",
      description =
          "Uninstalls the installation as DELETE /installations/{id} does. An installation of"
              + " another Data pool is not found, and nothing changes.")
  @ApiResponse(responseCode = "204", description = "The installation is uninstalled")
  @ApiResponse(
      responseCode = "404",
      description = "Installation not found in the Data pool",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "An artifact of the installation cannot be removed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> uninstall(@PathVariable UUID datapoolId, @PathVariable UUID id) {
    uninstallService.uninstallInDatapool(datapoolId, id);
    return ResponseEntity.noContent().build();
  }
}
