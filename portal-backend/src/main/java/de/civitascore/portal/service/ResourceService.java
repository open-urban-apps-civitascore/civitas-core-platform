package de.civitascore.portal.service;

import de.civitascore.portal.mapper.ResourceMapper;
import de.civitascore.portal.model.entity.Resource;
import de.civitascore.portal.model.input.ResourceInputDTO;
import de.civitascore.portal.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceService extends BaseService<Resource, ResourceInputDTO> {

  private final ResourceRepository resourceRepository;
  private final ResourceMapper resourceMapper;

  @Override
  protected ResourceRepository getRepository() {
    return resourceRepository;
  }

  @Override
  protected ResourceMapper getMapper() {
    return resourceMapper;
  }

  @Override
  protected String getEntityName() {
    return Resource.class.getSimpleName();
  }
}
