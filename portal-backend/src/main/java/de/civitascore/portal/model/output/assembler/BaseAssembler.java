package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.output.BaseOutputDTO;
import org.springframework.data.domain.Page;

/** Assembles the output DTO of an entity: the mapper's fields, then the assembler's enrichments. */
public interface BaseAssembler<E extends BaseEntity, O extends BaseOutputDTO> {

  default O toOutput(E entity) {
    if (entity == null) return null;
    return enrichDto(mapToBaseDto(entity), entity);
  }

  default Page<O> toOutput(Page<E> entities) {
    return entities.map(this::toOutput);
  }

  O mapToBaseDto(E entity);

  /** Adds what the mapper cannot derive from the entity alone. Default: the DTO unchanged. */
  default O enrichDto(O dto, E entity) {
    return dto;
  }
}
