package de.civitascore.portal.controller;

import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.assembler.InstallationAssembler;
import de.civitascore.portal.service.InstallationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Installations are the resource: creating one installs a package, reading one says what that
 * install did. Deliberately one resource with a full lifecycle rather than a separate import path —
 * creating a thing somewhere other than where it is read explains itself to nobody.
 */
@RestController
@RequestMapping("/installations")
@RequiredArgsConstructor
@Tag(name = "Installations", description = "Install packages and read what an install did")
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
}
