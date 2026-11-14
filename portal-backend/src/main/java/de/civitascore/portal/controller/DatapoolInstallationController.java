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
 * REST controller for the installations of one Data pool. The operations are the same as in {@link
 * InstallationController}, under the path of the Data pool. OPA checks the permissions on the Data
 * pool of the path, so a grant on the Data pool is sufficient. This controller makes sure that each
 * installation that it reads or uninstalls is in that Data pool.
 *
 * <p>springdoc names each operation after its handler method, so the methods here have names of
 * their own, apart from the ones in {@link InstallationController}.
 */
@RestController
@RequestMapping("/datapools/{datapoolId}/installations")
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
      summary = "Install a package in a Data pool",
      description =
          "Installs the package as POST /installations does, in the Data pool of the path. The"
              + " request can leave out datapoolId. The record keeps the Data pool also for a"
              + " package without Data sources and without a Dataset. 400 means also that the"
              + " request names another Data pool.")
  @Problems({
    ProblemType.INVALID_INPUT,
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<InstallationOutputDTO> installInDatapool(
      @PathVariable UUID datapoolId, @Valid @RequestBody InstallationInputDTO input) {
    Installation installation = installationService.installInDatapool(datapoolId, input);
    return ResponseEntity.created(Locations.ofCreated(installation.getId()))
        .body(installationAssembler.toOutput(installation));
  }

  /**
   * Returns a page of the installations of a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param pageable pagination and sorting parameters
   * @return a page of installations with HTTP 200 status
   */
  @Operation(
      summary = "List the installations of a Data pool",
      description =
          "Returns a page of the installations of the Data pool, the active and the uninstalled"
              + " ones. Each installation contains its lines.")
  @Problems(ProblemType.NOT_FOUND)
  @GetMapping
  public ResponseEntity<Page<InstallationOutputDTO>> listDatapoolInstallations(
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
      summary = "Get an installation of a Data pool",
      description = "Returns one installation of the Data pool with its lines.")
  @Problems(ProblemType.NOT_FOUND)
  @GetMapping("/{id}")
  public ResponseEntity<InstallationOutputDTO> getDatapoolInstallation(
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
      summary = "Uninstall an installation of a Data pool",
      description =
          "Uninstalls the installation as DELETE /installations/{id} does. An installation of"
              + " another Data pool is not found, and nothing changes.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE, ProblemType.SAGA_IN_FLIGHT})
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> uninstallDatapoolInstallation(
      @PathVariable UUID datapoolId, @PathVariable UUID id) {
    uninstallService.uninstallInDatapool(datapoolId, id);
    return ResponseEntity.noContent().build();
  }
}
