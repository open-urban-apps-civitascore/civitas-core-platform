package de.civitascore.portal.service;

import de.civitascore.portal.mapper.ActivityMapper;
import de.civitascore.portal.model.entity.Activity;
import de.civitascore.portal.model.input.ActivityInputDTO;
import de.civitascore.portal.repository.ActivityRepository;
import de.civitascore.portal.repository.AgentRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

  @Override
  protected Activity postConvertToEntity(Activity entity, ActivityInputDTO input) {
    // Set agents
    Optional.ofNullable(input.getAgentIds())
        .map(agentRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setAgents);

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected Activity preSave(Activity entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(Activity entity) {
    activityRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    Activity.class.getSimpleName(), "name", entity.getName());
              }
            });
  }
}
