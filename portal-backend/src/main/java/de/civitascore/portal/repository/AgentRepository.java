package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Agent;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Agent} entities. */
@Repository
public interface AgentRepository extends BaseRepository<Agent, UUID> {}
