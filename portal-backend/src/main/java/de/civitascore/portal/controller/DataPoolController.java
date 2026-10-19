package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.input.DataPoolInputDTO;
import de.civitascore.portal.model.output.DataPoolOutputDTO;
import de.civitascore.portal.model.output.assembler.DataPoolAssembler;
import de.civitascore.portal.repository.specification.DataPoolSpec;
import de.civitascore.portal.service.DataPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing datapool resources. */
@RestController
@RequestMapping("/datapools")
@RequiredArgsConstructor
@Tag(name = "DataPools", description = "Datapool management endpoints")
public class DataPoolController
    extends BaseDataEntityController<DataPoolInputDTO, DataPoolOutputDTO, DataPool, DataPoolSpec> {

  private final DataPoolService dataPoolService;
  private final DataPoolAssembler dataPoolAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "mobility")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "governance")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "city"))
  })
  /** Overridden to apply {@code @ParameterObject} and pagination defaults for SpringDoc. */
  @Override
  public ResponseEntity<Page<DataPoolOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataPoolSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  protected DataPoolService getService() {
    return dataPoolService;
  }

  @Override
  protected DataPoolAssembler getAssembler() {
    return dataPoolAssembler;
  }

  @Override
  protected ScopeType getScopeType() {
    return ScopeType.DATAPOOL;
  }

  @Override
  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete a datapool",
      description =
          "Permanently deletes a datapool by its UUID. Fails with 409 Conflict if any datasets are still assigned to it.")
  @ApiResponse(responseCode = "204", description = "Datapool deleted successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Datapool not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Conflict — datasets are still assigned to this datapool",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    dataPoolService.deleteById(id);
  }
}
