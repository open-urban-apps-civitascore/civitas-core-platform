package de.civitascore.portal.repository;

import de.civitascore.portal.model.SchemaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchemaRepository extends JpaRepository<SchemaEntity, UUID> {}
