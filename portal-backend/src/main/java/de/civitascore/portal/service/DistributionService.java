package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DistributionMapper;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.input.DistributionInputDTO;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.ResourceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Distribution} entities, which represent access endpoints for a
 * dataset.
 */
@Service
@RequiredArgsConstructor
public class DistributionService extends BaseService<Distribution, DistributionInputDTO> {

  private final DistributionRepository distributionRepository;
  private final DistributionMapper distributionMapper;
  private final ResourceRepository resourceRepository;

  @Override
  protected DistributionRepository getRepository() {
    return distributionRepository;
  }

  @Override
  protected DistributionMapper getMapper() {
    return distributionMapper;
  }

  @Override
  protected String getEntityName() {
    return Distribution.class.getSimpleName();
  }

  /**
   * Resolves the resource reference after DTO-to-entity conversion. The dataset and activity
   * associations are managed by their owning entities and are not set here.
   *
   * @param entity the distribution entity
   * @param input the distribution input DTO containing the resource ID
   * @return the entity with the resource relationship set
   */
  @Override
  protected Distribution postConvertToEntity(Distribution entity, DistributionInputDTO input) {
    // Set resource
    Optional.ofNullable(input.getResourceId())
        .flatMap(resourceRepository::findById)
        .ifPresentOrElse(entity::setResource, () -> entity.setResource(null));

    // dataSet and activity are not updatable through input DTO
    // They are managed by their respective owning entities (DataSet/Activity)
    return super.postConvertToEntity(entity, input);
  }
}
