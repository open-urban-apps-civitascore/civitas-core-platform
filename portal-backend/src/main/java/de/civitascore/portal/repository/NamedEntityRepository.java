package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.base.NamedEntity;
import java.io.Serializable;
import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface NamedEntityRepository<T extends NamedEntity, ID extends Serializable>
    extends BaseRepository<T, ID> {

  /**
   * Find an entity by name.
   *
   * @param name the entity name
   * @return the entity if found
   */
  Optional<T> findByName(String name);
}
