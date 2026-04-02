package de.civitascore.portal.service;

import de.civitascore.portal.mapper.AgentMapper;
import de.civitascore.portal.model.entity.Agent;
import de.civitascore.portal.model.input.AgentInputDTO;
import de.civitascore.portal.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Service for managing {@link Agent} entities that represent processing agents in the platform. */
@Service
@RequiredArgsConstructor
public class AgentService extends BaseService<Agent, AgentInputDTO> {

  private final AgentRepository agentRepository;
  private final AgentMapper agentMapper;

  @Override
  protected AgentRepository getRepository() {
    return agentRepository;
  }

  @Override
  protected AgentMapper getMapper() {
    return agentMapper;
  }

  @Override
  protected String getEntityName() {
    return Agent.class.getSimpleName();
  }
}
