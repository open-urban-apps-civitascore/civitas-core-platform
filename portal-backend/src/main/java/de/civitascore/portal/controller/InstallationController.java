package de.civitascore.portal.controller;

import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.service.InstallationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the install provenance recorded by the bundle import: which bundle was installed when, by
 * whom, and what it created or reused. This is the platform-side answer to "which artifacts in this
 * instance came from an install" — clients (marketplace UI, CLIs) read it instead of keeping their
 * own bookkeeping.
 *
 * <p>Gated by {@code INSTALLATION_READ}, granted tenant-wide to administrative roles. The list is
 * deliberately not scope-filtered: provenance references datasets across scopes, so it is an audit
 * view for roles that may see the whole instance — not a per-scope resource.
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
}
