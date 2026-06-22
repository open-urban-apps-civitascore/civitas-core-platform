package de.civitascore.authz.repository.data;

import de.civitascore.portal.model.entity.DataSet;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only repository for dataset→datapool membership lookups.
 *
 * <p>Used by {@link de.civitascore.authz.repository.service.DatasetPoolService} to resolve the
 * datapool a single dataset belongs to (Epic 1 union inheritance). Reads the same portal database as
 * the portal backend via the shared {@code portal-model} entities.
 */
@Repository
public interface DataSetRepository extends JpaRepository<DataSet, UUID> {}
