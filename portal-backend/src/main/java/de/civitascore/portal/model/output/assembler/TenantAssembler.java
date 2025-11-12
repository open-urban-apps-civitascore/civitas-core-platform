package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.TenantMapper;
import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.model.output.TenantOutputDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantAssembler implements BaseAssembler<Tenant, TenantOutputDTO, String> {

  private final TenantMapper tenantMapper;

  @Override
  public TenantOutputDTO mapToBaseDto(Tenant entity) {
    return tenantMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Tenant entity) {
    return (I) tenantMapper.toInput(entity);
  }
}
