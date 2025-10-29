package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.model.input.TenantInputDTO;
import de.civitascore.portal.model.output.TenantOutputDTO;
import de.civitascore.portal.model.output.assembler.TenantAssembler;
import de.civitascore.portal.repository.specification.TenantSpec;
import de.civitascore.portal.service.TenantAwareService;
import de.civitascore.portal.service.TenantService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Tenant management endpoints")
public class TenantController
    extends BaseController<TenantInputDTO, TenantOutputDTO, Tenant, TenantSpec, String> {

  private final TenantService tenantService;
  private final TenantAssembler tenantAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Acme Corp")),
    @Parameter(
        name = "active",
        description = "Filter by active status.",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "boolean", example = "true")),
    @Parameter(
        name = "q",
        description = "Search in name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "acme"))
  })
  @Override
  public ResponseEntity<Page<TenantOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") TenantSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  protected TenantAwareService<Tenant, String, TenantInputDTO> getService() {
    return (TenantAwareService<Tenant, String, TenantInputDTO>) (Object) tenantService;
  }

  @Override
  protected TenantAssembler getAssembler() {
    return tenantAssembler;
  }

  @Override
  public ResponseEntity<TenantOutputDTO> create(TenantInputDTO input) {
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<TenantOutputDTO> update(
      @PathVariable String s, @Valid @RequestBody TenantInputDTO input) {
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<TenantOutputDTO> patch(
      @PathVariable String s, @RequestBody TenantInputDTO input) {
    return ResponseEntity.noContent().build();
  }

  @Override
  public void delete(@PathVariable String s) {}
}
