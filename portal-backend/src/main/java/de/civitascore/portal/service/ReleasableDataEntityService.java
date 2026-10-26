package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import de.civitascore.portal.util.InvalidInputException;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base service for data entities with a release lifecycle (DRAFT to AVAILABLE). Adds release,
 * unrelease and the metadata update of a released entity to {@link BaseDataEntityService}.
 *
 * @param <E> the data entity type
 * @param <I> the input DTO type
 * @param <M> the metadata input DTO type: the part of {@code I} that stays changeable after release
 */
public abstract class ReleasableDataEntityService<
        E extends BaseDataEntity, I extends M, M extends BaseDataEntityInputDTO>
    extends BaseDataEntityService<E, I> {

  // --- Status accessors (abstract — each concrete service implements) ---

  protected abstract ReleasableStatus getEntityStatus(E entity);

  protected abstract void setEntityStatus(E entity, ReleasableStatus status);

  protected abstract ReleasableStatus getDraftStatus();

  protected abstract ReleasableStatus getAvailableStatus();

  // --- Release lifecycle template methods ---

  @Transactional
  public E release(UUID id) {
    E entity = findByIdOrThrow(id);
    if (!getEntityStatus(entity).isDraft()) {
      throw new InvalidInputException(
          getEntityName(), id, "Only entities in DRAFT status can be released");
    }
    validateRelease(entity);
    setEntityStatus(entity, getAvailableStatus());
    return save(entity);
  }

  @Transactional
  public E unrelease(UUID id) {
    E entity = findByIdOrThrow(id);
    if (!getEntityStatus(entity).isAvailable()) {
      throw new InvalidInputException(
          getEntityName(), id, "Only entities in AVAILABLE status can be unreleased");
    }
    validateUnrelease(entity);
    setEntityStatus(entity, getDraftStatus());
    return save(entity);
  }

  /**
   * Applies metadata to a released entity. A field that {@link #toMetaInput} leaves {@code null},
   * such as {@code assignments}, keeps its value while it is still {@code null} here.
   *
   * @param id the entity ID
   * @param meta the metadata, typically {@link #toMetaInput} with a patch applied
   * @return the updated entity
   * @throws InvalidInputException if the entity is in DRAFT status
   */
  public abstract E updateReleasedMeta(UUID id, M meta);

  /**
   * Returns the current metadata of the entity, which a metadata patch starts from.
   *
   * @param entity the entity
   * @return the metadata, with {@code assignments} and any other field whose {@code null} means
   *     keep left {@code null}
   */
  public abstract M toMetaInput(E entity);

  // --- Validation hooks (default no-op, subclasses override) ---

  protected void validateRelease(E entity) {}

  protected void validateUnrelease(E entity) {}
}
