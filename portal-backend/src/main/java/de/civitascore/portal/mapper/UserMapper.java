package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.UserOutputDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper extends DtoMapper<UserInputDTO, UserOutputDTO, User> {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "groups", ignore = true)
  @Override
  User toEntity(UserInputDTO input);

  @Mapping(target = "groups", ignore = true)
  @Override
  UserOutputDTO toOutput(User entity);

  UserSummaryDTO toSummary(User entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "groups", ignore = true)
  @Override
  void updateEntity(@MappingTarget User entity, UserInputDTO input);
}
