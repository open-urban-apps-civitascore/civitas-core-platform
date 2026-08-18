package de.civitascore.portal.controller;

import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.service.InstallationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the install provenance recorded by the bundle import: which bundle was installed when, by
 * whom, and what it created or reused. This is the platform-side answer to "which artifacts in this
 * instance came from an install" — clients (marketplace UI, CLIs) read it instead of keeping their
 * own bookkeeping.
 *
 * <p>Gated by {@code INSTALLATION_READ}, granted tenant-wide to the Data Architect — installing is
 * a data concern, and the Tenant Admin role is defined as exactly the {@code TENANT_ADMINISTRATION}
 * permission category (an invariant {@code RoleInitializerTest} pins). The list is deliberately not
 * scope-filtered: provenance references datasets across scopes, so it is an audit view for roles
 * that may see the whole instance — not a per-scope resource.
 */
@RestController
@RequestMapping("/installations")
@RequiredArgsConstructor
@Tag(
    name = "Installations",
    description = "Provenance of bundle installs: bundle identity, actor, per-artifact actions")
public class InstallationController {

  private final InstallationService installationService;

  /**
   * Lists recorded installations, paged, newest first by default.
   *
   * @param pageable page, size and sort
   * @return one page of installations with their per-artifact actions
   */
  @Operation(summary = "List recorded bundle installations (paged, newest first)")
  @GetMapping
  public Page<InstallationOutputDTO> list(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return installationService.findAll(pageable);
  }

  /**
   * Uninstalls a structure-only installation. See {@link InstallationService#uninstall} for the
   * semantics; the provenance record itself survives as history with its {@code uninstalledAt} set.
   */
  @Operation(
      summary = "Uninstall a structure-only installation",
      description =
          "Deletes every data structure this installation CREATED (shell, versions and registry"
              + " model, through the regular delete path and all its guards) and marks the"
              + " installation uninstalled. REUSED artifacts are never deleted — uninstalling only"
              + " withdraws this installation's claim. Use-case installations are refused until"
              + " the bundle-teardown increment lands.")
  @ApiResponse(responseCode = "204", description = "Uninstalled; the record remains as history")
  @ApiResponse(
      responseCode = "400",
      description = "Already uninstalled, or the installation is not structure-only",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No such installation",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "A contained data structure is referenced by another active installation, or is still"
              + " in use by a data source or sink",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void uninstall(@PathVariable UUID id) {
    installationService.uninstall(id);
  }
}
