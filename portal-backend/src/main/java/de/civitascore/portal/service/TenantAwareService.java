package de.civitascore.portal.service;

import de.civitascore.portal.configuration.TenantContext;
import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.io.Serializable;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
public abstract class TenantAwareService<
    E extends BaseEntity<ID>, ID extends Serializable, I extends BaseInputDTO> {

  @Transactional
  public E create(I input) {
    I preProcessedInput = preProcessCreateInput(input);
    E entity = getMapper().toEntity(preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);
    E saved = getRepository().save(entity);
    postSave(saved, preProcessedInput);
    return saved;
  }

  @Transactional
  public E update(ID id, I input) {
    E entity = findById(id);
    I preProcessedInput = preProcessUpdateInput(input, entity);
    getMapper().updateEntity(entity, preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);
    E saved = getRepository().save(entity);
    postSave(saved, preProcessedInput);
    return saved;
  }

  public Page<E> findAll(Specification<E> spec, Pageable pageable) {
    Specification<E> enhancedSpec = preProcessQuery(spec, pageable);
    Page<E> result = getRepository().findAll(enhancedSpec, pageable);
    return postProcessQueryResult(result);
  }

  public E findById(ID id) {
    E entity = preProcessLoad(id);
    entity =
        getRepository()
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id.toString()));
    return postLoad(entity);
  }

  @Transactional
  public void deleteById(ID id) {
    if (!getRepository().existsById(id)) {
      throw new ResourceNotFoundException(getEntityName(), id.toString());
    }
    E entity = preProcessDelete(id);
    getRepository().deleteById(id);
    postDelete(entity);
  }

  protected I preProcessCreateInput(I input) {
    return input;
  }

  protected E postConvertToEntity(E entity, I input) {
    setTenantId(entity);
    return entity;
  }

  protected E preSave(E entity) {
    return entity;
  }

  protected void postSave(E entity, I input) {}

  protected I preProcessUpdateInput(I input, E existingEntity) {
    return input;
  }

  protected Specification<E> preProcessQuery(Specification<E> spec, Pageable pageable) {
    return spec;
  }

  protected Page<E> postProcessQueryResult(Page<E> result) {
    return result;
  }

  protected E preProcessLoad(ID id) {
    return getRepository().findById(id).orElse(null);
  }

  protected E postLoad(E entity) {
    return entity;
  }

  protected E preProcessDelete(ID id) {
    return getRepository().findById(id).orElse(null);
  }

  protected void postDelete(E entity) {}

  protected abstract TenantAwareRepository<E, ID> getRepository();

  protected abstract DtoMapper<I, ?, E> getMapper();

  protected abstract String getEntityName();

  protected String getCurrentTenantId() {
    String tenantId = TenantContext.getTenantId();
    if (tenantId == null) throw new IllegalStateException("No tenant context available");
    return tenantId;
  }

  protected void setTenantId(E entity) {
    String tenantId = getCurrentTenantId();
    try {
      Method setTenantId = entity.getClass().getMethod("setTenantId", String.class);
      setTenantId.invoke(entity, tenantId);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set tenant ID", e);
    }
  }
}
