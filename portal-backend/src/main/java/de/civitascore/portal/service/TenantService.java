package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.repository.BaseRepository;
import de.civitascore.portal.repository.TenantRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService extends BaseService<Tenant, String> {

  private final TenantRepository tenantRepository;

  @Override
  protected BaseRepository<Tenant, String> getRepository() {
    return tenantRepository;
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
