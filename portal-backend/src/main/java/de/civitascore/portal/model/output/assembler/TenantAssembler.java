package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.TenantMapper;
import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.model.output.TenantOutputDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Minimal Tenant assembler using your mapper + basic enrichments. */
@Component
@RequiredArgsConstructor
public class TenantAssembler implements EntityAssembler<Tenant, TenantOutputDTO, String> {

  private final TenantMapper tenantMapper;

  @Override
  public TenantOutputDTO mapToBaseDto(Tenant entity) {
    return tenantMapper.toOutput(entity); // Your mapper's basic field mapping
  }
}
