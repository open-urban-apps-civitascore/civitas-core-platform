package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.model.output.DashboardOutputDTO;
import de.civitascore.portal.model.output.assembler.DashboardAssembler;
import de.civitascore.portal.repository.specification.VisualizationSpec;
import de.civitascore.portal.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.MethodNotAllowedException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/datasets/{dataSetId}/visualizations")
@RequiredArgsConstructor
@Tag(
    name = "Visualizations",
    description = "Dataset visualization (dashboard) management endpoints")
public class VisualizationController
    extends BaseController<DashboardInputDTO, DashboardOutputDTO, Dashboard, VisualizationSpec> {

  private final DashboardService dashboardService;
  private final DashboardAssembler dashboardAssembler;

  @Override
  protected DashboardService getService() {
    return dashboardService;
  }

  @Override
  protected DashboardAssembler getAssembler() {
    return dashboardAssembler;
  }

  @Override
  @Operation(operationId = "listVisualizations", summary = "List visualizations for a dataset")
  public ResponseEntity<Page<DashboardOutputDTO>> getAll(
      @ParameterObject VisualizationSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  @Operation(operationId = "getVisualization", summary = "Get visualization by id")
  public ResponseEntity<DashboardOutputDTO> getById(@PathVariable UUID id) {
    Dashboard dashboard = dashboardService.findByIdAndDataSetOrThrow(id, extractDataSetId());
    return ResponseEntity.ok(dashboardAssembler.toOutput(dashboard));
  }

  @Override
  @Operation(operationId = "createVisualization", summary = "Create a visualization")
  public ResponseEntity<DashboardOutputDTO> create(@Valid @RequestBody DashboardInputDTO input) {
    return super.create(input);
  }

  @Override
  @Operation(operationId = "updateVisualization", summary = "Replace a visualization")
  public ResponseEntity<DashboardOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody DashboardInputDTO input) {
    dashboardService.findByIdAndDataSetOrThrow(id, extractDataSetId());
    return super.update(id, input);
  }

  @Override
  @Operation(operationId = "deleteVisualization", summary = "Delete a visualization")
  public void delete(@PathVariable UUID id) {
    dashboardService.findByIdAndDataSetOrThrow(id, extractDataSetId());
    super.delete(id);
  }

  // Visualizations are replace-only (PUT); disabling PATCH here is also what makes the assembler's
  // toInput() — and its unchecked cast — dead code on this controller, since PATCH is its only
  // caller.
  @Override
  public ResponseEntity<DashboardOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) throws IOException {
    throw new MethodNotAllowedException(
        HttpMethod.PATCH, Set.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE));
  }

  @Override
  protected DashboardInputDTO preProcessInput(DashboardInputDTO input) {
    input.setDataSetId(extractDataSetId());
    return super.preProcessInput(input);
  }

  private UUID extractDataSetId() {
    return extractUUIDFromPathVariable("dataSetId", Dashboard.class);
  }
}
