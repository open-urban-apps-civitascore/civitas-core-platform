package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSpaceMapper;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSpaceService extends TenantAwareService<DataSpace, String, DataSpaceInputDTO> {

  private final DataSpaceRepository dataSpaceRepository;
  private final DataSpaceMapper dataSpaceMapper;
  private final UserService userService;

  @Override
  protected TenantAwareRepository<DataSpace, String> getRepository() {
    return dataSpaceRepository;
  }

  @Override
  protected DataSpaceMapper getMapper() {
    return dataSpaceMapper;
  }

  @Override
  protected String getEntityName() {
    return "DataSpace";
  }

  public Optional<DataSpace> findByTitle(String name, String tenantId) {
    return dataSpaceRepository.findByTitleAndTenantId(name, tenantId);
  }

  public Optional<DataSpace> findByExternalId(String externalId, String tenantId) {
    return dataSpaceRepository.findByExternalIdAndTenantId(externalId, tenantId);
  }

  public List<DataSpace> findByOwner(String ownerId, String tenantId) {
    return dataSpaceRepository.findByOwnerIdAndTenantId(ownerId, tenantId);
  }

  public List<DataSpace> findRootDataSpaces(String tenantId) {
    return dataSpaceRepository.findByParentDataSpaceIsNullAndTenantId(tenantId);
  }

  @Override
  protected DataSpace postConvertToEntity(DataSpace entity, DataSpaceInputDTO input) {
    if (Objects.nonNull(input.getOwnerUserId())) {
      entity.setOwner(userService.findById(input.getOwnerUserId()));
    }

    if (Objects.nonNull(input.getParentDataSpaceId())) {
      dataSpaceRepository
          .findById(input.getParentDataSpaceId())
          .ifPresent(entity::setParentDataSpace);
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSpace preSave(DataSpace entity) {
    validateUniqueTitle(entity);
    return super.preSave(entity);
  }

  private void validateUniqueTitle(DataSpace entity) {
    dataSpaceRepository
        .findByTitleAndTenantId(entity.getTitle(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "DataSpace", "title", entity.getTitle(), "tenant", entity.getTenantId());
              }
            });
  }
}
