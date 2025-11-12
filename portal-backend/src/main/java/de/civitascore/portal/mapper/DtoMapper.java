package de.civitascore.portal.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Generic base interface for DTO-to-Entity mappers using MapStruct.
 *
 * <p>Provides standard methods for bidirectional conversion between DTOs and JPA entities,
 * separating input DTOs (for create/update) from output DTOs (for responses).
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * @Mapper(componentModel = "spring")
 * public interface UserMapper extends DtoMapper<UserInputDTO, UserOutputDTO, User> {
 *   // Override specific mappings as needed
 * }
 * }</pre>
 *
 * @param <I> Input DTO type for incoming requests (Create/Update)
 * @param <O> Output DTO type for outgoing responses
 * @param <E> JPA Entity type
 * @see org.mapstruct.Mapper
 * @author Civitas Core Platform
 */
public interface DtoMapper<I, O, E> {

  /**
   * Converts an input DTO to a new JPA entity.
   *
   * <p>Used for create operations. Audit fields (id, createdAt, modifiedAt, createdBy, modifiedBy)
   * are automatically ignored and handled by the persistence layer.
   *
   * @param input the input DTO with data to map
   * @return a new entity instance with mapped values
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "modifiedBy", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  E toEntity(I input);

  /**
   * Converts a JPA entity to an output DTO.
   *
   * <p>Used for read operations to expose only API-relevant data to consumers.
   *
   * @param entity the entity to convert
   * @return an output DTO with mapped values
   */
  O toOutput(E entity);

  /**
   * Converts a JPA entity back to an input DTO.
   *
   * <p>Typically used for update forms to load existing entity data into an editable DTO.
   *
   * @param entity the entity to convert
   * @return an input DTO with mapped values
   */
  I toInput(E entity);

  /**
   * Updates an existing entity with values from an input DTO.
   *
   * <p>Implements PATCH semantics with explicit null-handling. Null values in the input DTO are
   * transferred to the entity. Protected fields (id, audit fields) are preserved.
   *
   * <p>Example:
   *
   * <pre>{@code
   * User user = userRepository.findById(id).orElseThrow();
   * userMapper.updateEntity(user, userInputDTO);
   * userRepository.save(user);
   * }</pre>
   *
   * @param entity the entity to update (modified in-place)
   * @param input the input DTO with new values
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "modifiedBy", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  void updateEntity(@MappingTarget E entity, I input);
}
