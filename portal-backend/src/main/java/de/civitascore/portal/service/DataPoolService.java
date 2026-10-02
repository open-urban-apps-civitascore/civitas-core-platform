package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataPoolMapper;
import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.DataPoolInputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link DataPool} entities. Enforces the constraints that a datapool cannot
 * be deleted while datasets are still assigned to it or while datasources are still scoped to it.
 */
@Service
public class DataPoolService
    extends BaseDataEntityService<DataPool, DataPoolInputDTO, DataPoolInputDTO> {

  private final DataPoolRepository dataPoolRepository;
  private final DataPoolMapper dataPoolMapper;
  private final DataSetRepository dataSetRepository;
  private final DataSourceRepository dataSourceRepository;
  private final UserRepository userRepository;
  private final AssignmentFactory assignmentFactory;

  public DataPoolService(
      DataPoolRepository dataPoolRepository,
      DataPoolMapper dataPoolMapper,
      DataSetRepository dataSetRepository,
      DataSourceRepository dataSourceRepository,
      UserRepository userRepository,
      AssignmentFactory assignmentFactory) {
    this.dataPoolRepository = dataPoolRepository;
    this.dataPoolMapper = dataPoolMapper;
    this.dataSetRepository = dataSetRepository;
    this.dataSourceRepository = dataSourceRepository;
    this.userRepository = userRepository;
    this.assignmentFactory = assignmentFactory;
  }

  @Override
  protected DataPoolRepository getRepository() {
    return dataPoolRepository;
  }

  @Override
  protected DataPoolMapper getMapper() {
    return dataPoolMapper;
  }

  @Override
  protected String getEntityName() {
    return DataPool.class.getSimpleName();
  }

  @Override
  protected AssignmentFactory getAssignmentFactory() {
    return assignmentFactory;
  }

  @Override
  protected ReleasableStatus getEntityStatus(DataPool entity) {
    throw new UnsupportedOperationException("DataPool does not support release lifecycle");
  }

  @Override
  protected void setEntityStatus(DataPool entity, ReleasableStatus status) {
    throw new UnsupportedOperationException("DataPool does not support release lifecycle");
  }

  @Override
  protected ReleasableStatus getDraftStatus() {
    throw new UnsupportedOperationException("DataPool does not support release lifecycle");
  }

  @Override
  protected ReleasableStatus getAvailableStatus() {
    throw new UnsupportedOperationException("DataPool does not support release lifecycle");
  }

  @Override
  public DataPool updateReleasedMeta(UUID id, DataPoolInputDTO meta) {
    throw new UnsupportedOperationException("DataPool does not support release lifecycle");
  }

  @Override
  public DataPoolInputDTO toMetaInput(DataPool entity) {
    throw new UnsupportedOperationException("DataPool does not support release lifecycle");
  }

  /**
   * Resolves the optional {@code contactPersonId} into the managed {@link User} entity. Null is
   * accepted; an unknown ID is rejected with {@link ResourceNotFoundException} (HTTP 404).
   */
  @Override
  protected DataPool postConvertToEntity(DataPool entity, DataPoolInputDTO input) {
    DataPool processed = super.postConvertToEntity(entity, input);
    if (input.getContactPersonId() == null) {
      processed.setContactPerson(null);
      return processed;
    }
    User contactPerson =
        userRepository
            .findById(input.getContactPersonId())
            .orElseThrow(() -> new ResourceNotFoundException("User", input.getContactPersonId()));
    processed.setContactPerson(contactPerson);
    return processed;
  }

  /**
   * Deletes a datapool by ID. Fails if any datasets are still assigned to this datapool.
   *
   * @param id the datapool ID
   * @throws ResourceNotFoundException if no datapool with the given ID exists
   * @throws ResourceInUseException if datasets are still assigned to this datapool
   */
  @Override
  @Transactional
  public void deleteById(UUID id) {
    findByIdOrThrow(id);
    if (dataSetRepository.existsByDataPoolId(id)) {
      throw new ResourceInUseException(
          "DataPool", id, "Cannot delete datapool — Dataset(s) are still assigned to it");
    }
    if (dataSourceRepository.existsByScopedDataPools_Id(id)) {
      throw new ResourceInUseException(
          "DataPool", id, "Cannot delete datapool — DataSource(s) are still scoped to it");
    }
    super.deleteById(id);
  }
}
