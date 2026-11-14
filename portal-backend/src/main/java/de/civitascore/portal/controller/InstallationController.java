package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.assembler.InstallationAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.service.InstallationService;
import de.civitascore.portal.service.UninstallService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for installations. A POST installs a package on this instance. A GET returns the
 * record of an install: the package and the artifacts that the install created. A DELETE removes
 * these artifacts and keeps the record.
 */
@RestController
@RequestMapping("/installations")
@RequiredArgsConstructor
@Tag(
    name = "Installations",
    description = "Install and uninstall packages, and read the record of each install")
public class InstallationController {

  private final InstallationService installationService;
  private final UninstallService uninstallService;
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
              + " DRAFT. If one member fails, the install keeps nothing. The same package can be"
              + " installed in more than one Data pool. 409 means that the package is already"
              + " installed in the Data pool, or, for a package without Data sources and without a"
              + " Dataset, on this instance.")
  @Problems({
    ProblemType.INVALID_INPUT,
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<InstallationOutputDTO> install(
      @Valid @RequestBody InstallationInputDTO input) {
    Installation installation = installationService.install(input);
    return ResponseEntity.created(Locations.ofCreated(installation.getId()))
        .body(installationAssembler.toOutput(installation));
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
  @Problems(ProblemType.NOT_FOUND)
  @GetMapping("/{id}")
  public ResponseEntity<InstallationOutputDTO> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(
        installationAssembler.toOutput(installationService.findByIdOrThrow(id)));
  }

  /**
   * Uninstalls an installation: removes the artifacts that the install created and keeps the
   * record.
   *
   * @param id the id of the installation
   * @return HTTP 204 status
   */
  @Operation(
      summary = "Uninstall an installation",
      description =
          "Removes the artifacts that the install created, in the reverse order of the install, and"
              + " keeps the record with the time of the uninstall. The Dataset goes with all that"
              + " it contains. The uninstall unreleases nothing: a released Dataset or Data"
              + " source, a Dataset with infrastructure, and an artifact that something else uses"
              + " stop it, and then it changes nothing. An artifact that no longer exists is"
              + " skipped. A second uninstall changes nothing.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE, ProblemType.SAGA_IN_FLIGHT})
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> uninstall(@PathVariable UUID id) {
    uninstallService.uninstall(id);
    return ResponseEntity.noContent().build();
  }
}
