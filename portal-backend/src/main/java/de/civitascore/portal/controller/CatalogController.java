package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.input.CatalogInputDTO;
import de.civitascore.portal.model.output.CatalogOutputDTO;
import de.civitascore.portal.model.output.assembler.CatalogAssembler;
import de.civitascore.portal.repository.specification.CatalogSpec;
import de.civitascore.portal.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing catalog resources with JSON and JSON-LD content negotiation. */
@Profile("preview")
@RestController
@RequestMapping("/catalogs")
@RequiredArgsConstructor
@Tag(name = "Catalogs", description = "Catalog management endpoints with content negotiation")
public class CatalogController
    extends BaseController<CatalogInputDTO, CatalogOutputDTO, Catalog, CatalogSpec> {

  private final CatalogService catalogService;
  private final CatalogAssembler catalogAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Public Catalog")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Open data catalog")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "public"))
  })
  /**
   * Retrieves a paginated list of catalogs with optional filtering by name, description, or
   * free-text search.
   *
   * @param spec the catalog search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of catalog output DTOs with HTTP 200 status
   */
  @Override
  public ResponseEntity<Page<CatalogOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") CatalogSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @GetMapping(
      value = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, "application/ld+json"})
  @Operation(
      summary = "Get catalog by ID",
      description =
          "Returns a catalog in JSON or JSON-LD format based on Accept header. "
              + "Use 'Accept: application/ld+json' for JSON-LD output, "
              + "or 'Accept: application/json' for standard JSON output.")
  /**
   * Retrieves a catalog by ID, supporting both JSON and JSON-LD content negotiation.
   *
   * @param id the UUID of the catalog to retrieve
   * @return the catalog output DTO with HTTP 200 status
   */
  @Override
  public ResponseEntity<CatalogOutputDTO> getById(@PathVariable UUID id) {
    return super.getById(id);
  }

  /** {@inheritDoc} */
  @Override
  protected CatalogService getService() {
    return catalogService;
  }

  /** {@inheritDoc} */
  @Override
  protected CatalogAssembler getAssembler() {
    return catalogAssembler;
  }
}
