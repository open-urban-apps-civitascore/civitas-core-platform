package de.civitascore.portal.mapper;

import org.mapstruct.MappingTarget;

public interface DtoMapper<I, O, E> {
  E toEntity(I input);

  O toOutput(E entity);

  void updateEntity(@MappingTarget E entity, I input);
}
