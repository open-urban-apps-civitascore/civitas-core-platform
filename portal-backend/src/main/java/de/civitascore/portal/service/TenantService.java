package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.mapper.TenantMapper;
import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.model.input.TenantInputDTO;
import de.civitascore.portal.repository.BaseRepository;
import de.civitascore.portal.repository.TenantRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService extends BaseService<Tenant, String, TenantInputDTO> {

  private final TenantRepository tenantRepository;
  private final TenantMapper tenantMapper;

  @Override
  protected BaseRepository<Tenant, String> getRepository() {
    return tenantRepository;
  }

  @Override
  protected String getEntityName() {
    return "Tenant";
  }

  protected DtoMapper<TenantInputDTO, ?, Tenant> getMapper() {
    return tenantMapper;
  }

  @Override
  @Transactional
  public Tenant create(TenantInputDTO input) {
    Tenant entity = getMapper().toEntity(input);
    validateUniqueName(entity);
    return getRepository().save(entity);
  }

  @Override
  @Transactional
  public Tenant update(String id, TenantInputDTO input) {
    Tenant entity = findById(id);
    getMapper().updateEntity(entity, input);
    validateUniqueName(entity);
    return getRepository().save(entity);
  }

  @Override
  @Transactional
  public Tenant save(Tenant entity) {
    validateUniqueName(entity);
    return super.save(entity);
  }

  private void validateUniqueName(Tenant entity) {
    tenantRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException("Tenant", "name", entity.getName());
              }
            });
  }
}
