package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSourceAssembler;
import de.civitascore.portal.repository.specification.DataSourceSpec;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.service.DataSourceService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing data source resources, including release/unrelease lifecycle. */
@RestController
@RequestMapping("/datasources")
@RequiredArgsConstructor
@Tag(name = "DataSources", description = "Data source management endpoints")
public class DataSourceController
    extends BaseDataEntityController<
        DataSourceInputDTO, DataSourceOutputDTO, DataSource, DataSourceSpec> {

  private final DataSourceService dataSourceService;
  private final DataSourceAssembler dataSourceAssembler;

  /** {@inheritDoc} */
  @Override
  protected DataSourceService getService() {
    return dataSourceService;
  }

  /** {@inheritDoc} */
  @Override
  protected DataSourceAssembler getAssembler() {
    return dataSourceAssembler;
  }

  /** {@inheritDoc} */
  @Override
  protected ScopeType getScopeType() {
    return ScopeType.DATASOURCE;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Widens data source scope filtering with datapool inheritance: in addition to directly scoped
   * data source IDs, the data sources usable in a datapool the caller has a DATAPOOL-scoped grant
   * on are visible, so a pool-scoped steward can see what their pipelines may be built from.
   */
  @Override
  protected Specification<DataSource> scopeSpecification(AllowedScopes scopes) {
    return ScopeFilteringSpecification.dataSourceByScopeOrPool(
        scopes.getScopeIds(), scopes.getPoolIds());
  }

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "mqtt-sensor")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Temperature sensor")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "sensor")),
    @Parameter(
        name = "dataSourceStatus",
        description = "Filter by status (exact match).",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"DRAFT", "AVAILABLE"})),
    @Parameter(
        name = "connectorType",
        description = "Filter by connector type (exact match).",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"MQTT", "SQL"})),
    @Parameter(
        name = "datapoolId",
        description =
            "Filter by DataPool scope. Returns DataSources with scope type ALL, or SPECIFIC DataSources that include this DataPool.",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                format = "uuid",
                example = "550e8400-e29b-41d4-a716-446655440000")),
    @Parameter(
        name = "datapoolScopeType",
        description = "Filter by DataPool scope type (exact match).",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"ALL", "NONE", "SPECIFIC"}))
  })
  /**
   * Retrieves a paginated list of data sources with optional filtering by name, description,
   * status, connector type, or free-text search.
   *
   * @param spec the data source search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of data source output DTOs with HTTP 200 status
   */
  @Override
  public ResponseEntity<Page<DataSourceOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataSourceSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /**
   * Merges connector-specific configuration fields during a JSON-merge patch.
   *
   * <p>Delegates to {@link DataSourceService#mergeConfigurationForPatch} after the standard Jackson
   * merge so that nested connector properties are handled correctly.
   *
   * <p>{@inheritDoc}
   */
  @Override
  protected DataSourceInputDTO patchInput(
      DataSourceInputDTO currentDto, DataSource entity, JsonNode updates) throws IOException {
    DataSourceInputDTO patchedDto = super.patchInput(currentDto, entity, updates);
    getService().mergeConfigurationForPatch(patchedDto, entity, updates);
    return patchedDto;
  }
}
