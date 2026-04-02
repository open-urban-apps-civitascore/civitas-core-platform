package de.civitascore.portal.repository;

import java.io.Serializable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository interface combining {@link JpaRepository} and {@link JpaSpecificationExecutor}.
 *
 * <p>All entity-specific repositories extend this interface to inherit standard CRUD operations and
 * specification-based querying.
 *
 * @param <T> the entity type
 * @param <ID> the entity ID type
 */
@NoRepositoryBean
public interface BaseRepository<T, ID extends Serializable>
    extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {}
