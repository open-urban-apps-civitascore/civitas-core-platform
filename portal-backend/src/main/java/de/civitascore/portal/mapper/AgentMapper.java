package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Agent;
import de.civitascore.portal.model.input.AgentInputDTO;
import de.civitascore.portal.model.output.AgentOutputDTO;
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
public interface AgentMapper extends DtoMapper<AgentInputDTO, AgentOutputDTO, Agent> {
  @Mapping(target = "dataSets", ignore = true)
  @Mapping(target = "activities", ignore = true)
  @Override
  Agent toEntity(AgentInputDTO input);

  @Override
  AgentOutputDTO toOutput(Agent entity);

  @Override
  AgentInputDTO toInput(Agent entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSets", ignore = true)
  @Mapping(target = "activities", ignore = true)
  @Override
  void updateEntity(@MappingTarget Agent entity, AgentInputDTO input);
}
