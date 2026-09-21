package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.PublishedStructure;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The pins of the structures the sink ports publish, one row per port. */
@Repository
public interface PublishedStructureRepository extends JpaRepository<PublishedStructure, UUID> {

  Optional<PublishedStructure> findByPort(String port);
}
