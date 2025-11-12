package de.civitascore.portal.repository;

import jakarta.persistence.EntityNotFoundException;
import java.io.Serializable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseRepository<T, ID extends Serializable>
    extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

  /**
   * Get a reference to an entity by ID, throwing an exception if it doesn't exist. This is a safer
   * alternative to {@link JpaRepository#getReferenceById(Object)} which validates existence before
   * returning the proxy.
   *
   * @param id the entity ID
   * @return a reference to the entity (lazy proxy)
   * @throws EntityNotFoundException if the entity doesn't exist
   */
  default T getReferenceByIdOrThrow(ID id) {
    if (!existsById(id)) {
      throw new EntityNotFoundException("Entity with id " + id + " not found");
    }
    return getReferenceById(id);
  }
}
