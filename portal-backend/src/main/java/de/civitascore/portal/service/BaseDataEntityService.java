package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import de.civitascore.portal.util.InvalidInputException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base service for entities that carry scoped {@link Assignment} collections. Extends {@link
 * BaseService} by converting assignment input DTOs to entities via the {@link AssignmentFactory}
 * during the post-conversion lifecycle hook.
 */
public abstract class BaseDataEntityService<
        E extends BaseDataEntity, I extends M, M extends BaseDataEntityInputDTO>
    extends BaseService<E, I> {

  /**
   * Returns the assignment factory used to build assignment entities from input DTOs.
   *
   * @return the assignment factory
   */
  protected abstract AssignmentFactory getAssignmentFactory();

  /**
   * Converts assignment input DTOs to entities via the {@link AssignmentFactory} and sets them on
   * the data entity after the base DTO-to-entity conversion.
   *
   * @param entity the data entity
   * @param input the input DTO containing assignment definitions
   * @return the entity with resolved assignments
   */
  @Override
  protected E postConvertToEntity(E entity, I input) {
    if (input.getAssignments() != null) {
      Set<Assignment> assignments =
          input.getAssignments().stream()
              .map(dto -> getAssignmentFactory().build(dto))
              .collect(Collectors.toSet());
      entity.setAssignments(assignments);
    }
    return super.postConvertToEntity(entity, input);
  }

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
   * @return the metadata, with {@code assignments} and any other field a patch may omit left {@code
   *     null}
   */
  public abstract M toMetaInput(E entity);

  // --- Validation hooks (default no-op, subclasses override) ---

  protected void validateRelease(E entity) {}

  protected void validateUnrelease(E entity) {}
}
