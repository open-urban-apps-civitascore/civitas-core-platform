package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSetService extends TenantAwareService<DataSet, String, DataSetInputDTO> {

  private final DataSetRepository dataSetRepository;
  private final DataSetMapper dataSetMapper;
  private final UserService userService;
  private final DataSpaceService dataSpaceService;

  @Override
  protected TenantAwareRepository<DataSet, String> getRepository() {
    return dataSetRepository;
  }

  @Override
  protected DataSetMapper getMapper() {
    return dataSetMapper;
  }

  @Override
  protected String getEntityName() {
    return "DataSet";
  }

  public Optional<DataSet> findByTitle(String name, String tenantId) {
    return dataSetRepository.findByTitleAndTenantId(name, tenantId);
  }

  public Optional<DataSet> findByExternalId(String externalId, String tenantId) {
    return dataSetRepository.findByExternalIdAndTenantId(externalId, tenantId);
  }

  public List<DataSet> findByOwner(String ownerId, String tenantId) {
    return dataSetRepository.findByOwnerIdAndTenantId(ownerId, tenantId);
  }

  @Override
  protected DataSet postConvertToEntity(DataSet entity, DataSetInputDTO input) {
    if (Objects.nonNull(input.getOwnerUserId())) {
      entity.setOwner(userService.findById(input.getOwnerUserId()));
    }

    if (Objects.nonNull(input.getDataSpaceIds())) {
      entity.setDataSpaces(new HashSet<>());
      if (!input.getDataSpaceIds().isEmpty()) {
        input
            .getDataSpaceIds()
            .forEach(
                dataSpaceId ->
                    dataSpaceService
                        .getRepository()
                        .findById(dataSpaceId)
                        .ifPresent(entity.getDataSpaces()::add));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSet preSave(DataSet entity) {
    validateUniqueTitle(entity);
    return super.preSave(entity);
  }

  private void validateUniqueTitle(DataSet entity) {
    dataSetRepository
        .findByTitleAndTenantId(entity.getTitle(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "DataSet", "title", entity.getTitle(), "tenant", entity.getTenantId());
              }
            });
  }
}
