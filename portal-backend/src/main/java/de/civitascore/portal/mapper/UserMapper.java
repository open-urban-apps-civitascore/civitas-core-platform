package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.UserOutputDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends DtoMapper<UserInputDTO, UserOutputDTO, User> {

  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "groups", ignore = true)
  @Override
  User toEntity(UserInputDTO input);

  @Mapping(target = "groups", ignore = true)
  @Override
  UserOutputDTO toOutput(User entity);

  @Mapping(target = "groupIds", ignore = true)
  @Override
  UserInputDTO toInput(User entity);

  @Mapping(
      target = "name",
      expression = "java(entity.getFirstName() + \" \" + entity.getLastName())")
  UserSummaryDTO toSummary(User entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "groups", ignore = true)
  @Override
  void updateEntity(@MappingTarget User entity, UserInputDTO input);
}
