package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.UseCase;
import de.civitascore.portal.model.input.UseCaseInputDTO;
import de.civitascore.portal.model.output.UseCaseOutputDTO;
import de.civitascore.portal.model.output.assembler.UseCaseAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.service.UninstallService;
import de.civitascore.portal.service.UseCaseService;
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
 * REST controller for the use cases of one Data pool. The operations are the same as in {@link
 * UseCaseController}, under the path of the Data pool. OPA checks the permissions on the Data pool
 * of the path, so a grant on the Data pool is sufficient. This controller makes sure that each use
 * case that it reads or uninstalls is in that Data pool.
 *
 * <p>springdoc names each operation after its handler method, so the methods here have names of
 * their own, apart from the ones in {@link UseCaseController}.
 */
@RestController
@RequestMapping("/datapools/{datapoolId}/use-cases")
@RequiredArgsConstructor
@Tag(
    name = "Use cases",
    description =
        "Install and uninstall packages as use cases, and read the record of each install")
public class DatapoolUseCaseController {

  private final UseCaseService useCaseService;
  private final UninstallService uninstallService;
  private final UseCaseAssembler useCaseAssembler;

  /**
   * Installs a package in a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param input the package to install
   * @return the use case, the record of the install, with HTTP 201 status
   */
  @Operation(
      summary = "Install a package in a Data pool",
      description =
          "Installs the package as POST /use-cases does, in the Data pool of the path. The request"
              + " can leave out datapoolId. The record keeps the Data pool also for a package"
              + " without Data sources and without a Dataset. 400 means also that the request"
              + " names another Data pool.")
  @Problems({
    ProblemType.INVALID_INPUT,
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<UseCaseOutputDTO> installInDatapool(
      @PathVariable UUID datapoolId, @Valid @RequestBody UseCaseInputDTO input) {
    UseCase useCase = useCaseService.installInDatapool(datapoolId, input);
    return ResponseEntity.created(Locations.ofCreated(useCase.getId()))
        .body(useCaseAssembler.toOutput(useCase));
  }

  /**
   * Returns a page of the use cases of a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param pageable pagination and sorting parameters
   * @return a page of use cases with HTTP 200 status
   */
  @Operation(
      summary = "List the use cases of a Data pool",
      description =
          "Returns a page of the use cases of the Data pool, the active and the uninstalled ones."
              + " Each use case contains its lines.")
  @Problems(ProblemType.NOT_FOUND)
  @GetMapping
  public ResponseEntity<Page<UseCaseOutputDTO>> listDatapoolUseCases(
      @PathVariable UUID datapoolId,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        useCaseAssembler.toOutput(useCaseService.findAllInDatapool(datapoolId, pageable)));
  }

  /**
   * Returns one use case of a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param id the id of the use case
   * @return the use case with HTTP 200 status
   */
  @Operation(
      summary = "Get a use case of a Data pool",
      description = "Returns one use case of the Data pool with its lines.")
  @Problems(ProblemType.NOT_FOUND)
  @GetMapping("/{id}")
  public ResponseEntity<UseCaseOutputDTO> getDatapoolUseCase(
      @PathVariable UUID datapoolId, @PathVariable UUID id) {
    return ResponseEntity.ok(
        useCaseAssembler.toOutput(useCaseService.findInDatapoolOrThrow(datapoolId, id)));
  }

  /**
   * Uninstalls a use case of a Data pool.
   *
   * @param datapoolId the id of the Data pool
   * @param id the id of the use case
   * @return HTTP 204 status
   */
  @Operation(
      summary = "Uninstall a use case of a Data pool",
      description =
          "Uninstalls the use case as DELETE /use-cases/{id} does. A use case of another Data pool"
              + " is not found, and nothing changes.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE, ProblemType.SAGA_IN_FLIGHT})
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> uninstallDatapoolUseCase(
      @PathVariable UUID datapoolId, @PathVariable UUID id) {
    uninstallService.uninstallInDatapool(datapoolId, id);
    return ResponseEntity.noContent().build();
  }
}
