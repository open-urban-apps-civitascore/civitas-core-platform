package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.PipelineRuntimeStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PipelineRuntimeStatusRepository
    extends JpaRepository<PipelineRuntimeStatus, UUID> {}
