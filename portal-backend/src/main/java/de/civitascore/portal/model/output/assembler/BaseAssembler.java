package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.output.BaseOutputDTO;
import java.io.Serializable;
import org.springframework.data.domain.Page;

/**
 * Generic assembler with pre/post hooks for output assembly. Template method: toOutput()
 * orchestrates assembly steps.
 */
public interface BaseAssembler<
    E extends BaseEntity, O extends BaseOutputDTO, ID extends Serializable> {

  /**
   * Convert entity back to input DTO for PATCH operations. Must be implemented by concrete
   * assemblers that need PATCH support. TODO: Add generic to the interface to enforce return type?
   */
  default <I> I toInput(E entity) {
    throw new UnsupportedOperationException(
        "toInput() must be implemented by the concrete assembler for PATCH support");
  }

  /**
   * Template method for entity to output conversion. Subclasses can override hooks but not this
   * method.
   */
  default O toOutput(E entity) {
    if (entity == null) return null;

    // Pre-assembly hook
    E preProcessed = preProcessEntity(entity);

    // Basic mapping (via injected mapper)
    O baseDto = mapToBaseDto(preProcessed);

    // Assembly-specific enrichments
    O enriched = enrichDto(baseDto, preProcessed);

    // Post-assembly hook
    return postProcessOutput(enriched, preProcessed);
  }

  /**
   * Converts a page of entities to a page of output DTOs using the {@link #toOutput(BaseEntity)}
   * template method.
   *
   * @param entities the page of entities to convert
   * @return a page of output DTOs
   */
  default Page<O> toOutput(Page<E> entities) {
    return entities.map(this::toOutput);
  }

  /**
   * Pre-processing hook: Modify entity before mapping (e.g., load relations). Default: No-op.
   * Override in implementations.
   */
  default E preProcessEntity(E entity) {
    return entity;
  }

  /**
   * Basic field mapping: Delegate to mapper's toOutput(). Must be implemented by concrete
   * assemblers.
   */
  O mapToBaseDto(E entity);

  /**
   * Enrichment hook: Add computed fields, format data for presentation. Default: No-op. Override
   * for entity-specific logic.
   */
  default O enrichDto(O dto, E entity) {
    return dto;
  }

  /**
   * Post-processing hook: Final touches (e.g., validation, caching). Default: No-op. Override if
   * needed.
   */
  default O postProcessOutput(O dto, E entity) {
    return dto;
  }

  /** Extract ID from output DTO. */
  default ID getIdFromOutput(O output) {
    try {
      return (ID) output.getId();
    } catch (ClassCastException e) {
      throw new IllegalStateException("Cannot extract ID from output DTO", e);
    }
  }
}
