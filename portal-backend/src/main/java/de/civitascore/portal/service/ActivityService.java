package de.civitascore.portal.service;

import de.civitascore.portal.mapper.ActivityMapper;
import de.civitascore.portal.model.entity.Activity;
import de.civitascore.portal.model.input.ActivityInputDTO;
import de.civitascore.portal.repository.ActivityRepository;
import de.civitascore.portal.repository.AgentRepository;
import java.util.HashSet;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Activity} entities. Resolves associated {@link
 * de.civitascore.portal.model.entity.Agent} references during entity conversion.
 */
@Service
@RequiredArgsConstructor
public class ActivityService extends BaseService<Activity, ActivityInputDTO> {

  private final ActivityRepository activityRepository;
  private final ActivityMapper activityMapper;
  private final AgentRepository agentRepository;

  @Override
  protected ActivityRepository getRepository() {
    return activityRepository;
  }

  @Override
  protected ActivityMapper getMapper() {
    return activityMapper;
  }

  @Override
  protected String getEntityName() {
    return Activity.class.getSimpleName();
  }

  /**
   * Resolves agent references after DTO-to-entity conversion by loading agents from the provided
   * IDs.
   *
   * @param entity the activity entity
   * @param input the activity input DTO containing agent IDs
   * @return the entity with resolved agent relationships
   */
  @Override
  protected Activity postConvertToEntity(Activity entity, ActivityInputDTO input) {
    // Set agents
    Optional.ofNullable(input.getAgentIds())
        .map(agentRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setAgents);

    return super.postConvertToEntity(entity, input);
  }
}
