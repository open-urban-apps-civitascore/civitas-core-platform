package de.civitascore.portal.controller;

import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.assembler.InstallationAssembler;
import de.civitascore.portal.service.InstallationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for installations. A POST installs a package on this instance. A GET returns the
 * record of an install: the package and the artifacts that the install created.
 */
@RestController
@RequestMapping(value = "/installations", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Installations", description = "Install packages and read the record of each install")
public class InstallationController {

  private final InstallationService installationService;
  private final InstallationAssembler installationAssembler;

  /**
   * Installs a package on this instance.
   *
   * @param input the package to install
   * @return the record of the install with HTTP 201 status
   */
  @Operation(
      summary = "Install a package",
      description =
          "Creates a copy of each member of the package on this instance and keeps a record of the"
              + " install. Each copy gets a URN of this instance. The record keeps the identity of"
              + " each member in the package as the origin of the copy. Each Data structure stays"
              + " DRAFT. If one member fails, the install keeps nothing.")
  @ApiResponse(responseCode = "201", description = "The package is installed")
  @ApiResponse(
      responseCode = "400",
      description = "The request is not valid, or the platform refuses a member",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The package is already installed on this instance",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<InstallationOutputDTO> install(
      @Valid @RequestBody InstallationInputDTO input) {
    InstallationOutputDTO output =
        installationAssembler.toOutput(installationService.install(input));
    return ResponseEntity.status(HttpStatus.CREATED).body(output);
  }

  /**
   * Returns a page of installations.
   *
   * @param pageable pagination and sorting parameters
   * @return a page of installations with HTTP 200 status
   */
  @Operation(
      summary = "List installations",
      description = "Returns a page of installations. Each installation contains its lines.")
  @ApiResponse(responseCode = "200", description = "Page of installations returned successfully")
  @GetMapping
  public ResponseEntity<Page<InstallationOutputDTO>> getAll(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(installationAssembler.toOutput(installationService.findAll(pageable)));
  }

  /**
   * Returns one installation.
   *
   * @param id the id of the installation
   * @return the installation with HTTP 200 status
   */
  @Operation(
      summary = "Get installation by ID",
      description = "Returns one installation with its lines.")
  @ApiResponse(responseCode = "200", description = "Installation returned successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Installation not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}")
  public ResponseEntity<InstallationOutputDTO> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(
        installationAssembler.toOutput(installationService.findByIdOrThrow(id)));
  }
}
