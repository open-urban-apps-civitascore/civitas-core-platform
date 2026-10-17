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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Installations are the resource: creating one installs a package, reading one says what that
 * install did, deleting one uninstalls it. Deliberately one resource with a full lifecycle rather
 * than a separate import path: creating a thing somewhere other than where it is read explains
 * itself to nobody.
 */
@RestController
@RequestMapping("/installations")
@RequiredArgsConstructor
@Tag(
    name = "Installations",
    description = "Install packages, read what an install did, and uninstall")
public class InstallationController {

  private final InstallationService installationService;
  private final InstallationAssembler installationAssembler;

  @Operation(summary = "Install a package on this instance")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InstallationOutputDTO install(@Valid @RequestBody InstallationInputDTO input) {
    return installationAssembler.toOutput(installationService.install(input));
  }

  @Operation(summary = "List installations, newest first")
  @GetMapping
  public Page<InstallationOutputDTO> list(Pageable pageable) {
    return installationService.findAll(pageable).map(installationAssembler::toOutput);
  }

  @Operation(summary = "Read one installation with its artifact lines")
  @GetMapping("/{id}")
  public InstallationOutputDTO get(@PathVariable UUID id) {
    return installationAssembler.toOutput(installationService.findByIdOrThrow(id));
  }

  /**
   * Uninstalls an installation. The record of the installation stays, so a read after the uninstall
   * returns it with the time of the uninstall.
   *
   * @param id the id of the installation
   */
  @Operation(
      summary = "Uninstall an installation",
      description =
          "Removes the artifacts that the installation created (204 No Content) and keeps the"
              + " record of the installation. The Dataset is removed with all that it contains."
              + " An artifact that no longer exists is skipped. An installation that is already"
              + " uninstalled stays as it is. After the uninstall, the package can be installed"
              + " again.")
  @ApiResponse(responseCode = "204", description = "The installation is uninstalled")
  @ApiResponse(
      responseCode = "404",
      description = "The installation does not exist",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Conflict: a Dataset of the installation is released, has infrastructure on the platform"
              + " or has an operation in progress, or something that the installation did not"
              + " create uses one of its artifacts. Nothing is removed.",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void uninstall(@PathVariable UUID id) {
    installationService.uninstall(id);
  }
}
