package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Agent;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentRepository extends BaseRepository<Agent, UUID> {}
