package de.civitascore.portal.service;

import de.civitascore.portal.repository.BaseRepository;
import java.io.Serializable;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public abstract class BaseService<T, ID extends Serializable> {

  protected abstract BaseRepository<T, ID> getRepository();

  public Page<T> findAll(Specification<T> spec, Pageable pageable) {
    return getRepository().findAll(spec, pageable);
  }

  public Optional<T> findById(ID id) {
    return getRepository().findById(id);
  }

  @Transactional
  public T save(T entity) {
    return getRepository().save(entity);
  }

  @Transactional
  public void deleteById(ID id) {
    getRepository().deleteById(id);
  }

  public boolean existsById(ID id) {
    return getRepository().existsById(id);
  }

  public long count() {
    return getRepository().count();
  }
}
