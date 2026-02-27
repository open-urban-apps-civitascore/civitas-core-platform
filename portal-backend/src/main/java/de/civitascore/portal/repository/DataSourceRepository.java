package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSource;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSourceRepository extends NamedEntityRepository<DataSource, UUID> {

  boolean existsByDataStructureVersionId(UUID dataStructureVersionId);

  @Query(
      "SELECT CASE WHEN EXISTS("
          + "SELECT 1 FROM DataSource ds WHERE ds.dataStructureVersion.id IN :versionIds"
          + ") THEN true ELSE false END")
  boolean existsByDataStructureVersionIdIn(@Param("versionIds") Collection<UUID> versionIds);
}
