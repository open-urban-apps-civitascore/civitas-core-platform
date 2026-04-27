package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.NamedApiOutputDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link DataSetInputDTO}, {@link DataSetOutputDTO}, and
 * {@link DataSet}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {
      DistributionMapper.class,
    },
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataSetMapper extends DtoMapper<DataSetInputDTO, DataSetOutputDTO, DataSet> {
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSetSeries", ignore = true)
  @Mapping(target = "dataSpaces", ignore = true)
  @Mapping(target = "agents", ignore = true)
  @Mapping(target = "distributions", ignore = true)
  @Mapping(target = "catalogs", ignore = true)
  @Mapping(target = "pipelines", ignore = true)
  @Mapping(target = "projectId", ignore = true)
  @Mapping(target = "frostBaseUrl", ignore = true)
  @Mapping(target = "serviceId", ignore = true)
  @Mapping(target = "publicUrl", ignore = true)
  @Mapping(target = "pipelineIds", ignore = true)
  @Mapping(target = "pendingSagaType", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  // namedApis must be wired through DataSet.setNamedApis(...) so the FK back-reference is set on
  // each child. MapStruct's default direct field set / Lombok-builder path bypasses that setter,
  // so we ignore it here and apply the collection in @AfterMapping below.
  @Mapping(target = "namedApis", ignore = true)
  @Override
  DataSet toEntity(DataSetInputDTO input);

  @Mapping(target = "createdBy", ignore = true)
  @Override
  DataSetOutputDTO toOutput(DataSet entity);

  @Mapping(target = "assignments", ignore = true)
  @Override
  DataSetInputDTO toInput(DataSet entity);

  /**
   * Maps the entity's named APIs back to input DTOs for PATCH round-tripping. Triggered by {@link
   * #toInput(DataSet)} via the {@code namedApis} field name match.
   */
  default List<NamedApiInputDTO> namedApisToInputDtos(Set<NamedApi> entities) {
    if (entities == null) {
      return new ArrayList<>();
    }
    List<NamedApiInputDTO> result = new ArrayList<>(entities.size());
    for (NamedApi e : entities) {
      result.add(toNamedApiInputDto(e));
    }
    return result;
  }

  DataSetSummaryDTO toSummary(DataSet entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSetSeries", ignore = true)
  @Mapping(target = "dataSpaces", ignore = true)
  @Mapping(target = "agents", ignore = true)
  @Mapping(target = "distributions", ignore = true)
  @Mapping(target = "catalogs", ignore = true)
  @Mapping(target = "pipelines", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "projectId", ignore = true)
  @Mapping(target = "frostBaseUrl", ignore = true)
  @Mapping(target = "serviceId", ignore = true)
  @Mapping(target = "publicUrl", ignore = true)
  @Mapping(target = "pipelineIds", ignore = true)
  @Mapping(target = "pendingSagaType", ignore = true)
  // Same rationale as toEntity: route through setNamedApis(...) for FK linkage.
  @Mapping(target = "namedApis", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSet entity, DataSetInputDTO input);

  /**
   * Wires {@code namedApis} through {@link DataSet#setNamedApis(java.util.Collection)} so the FK
   * back-reference is set on each entry. Used by both {@link #toEntity(DataSetInputDTO)} (POST) and
   * {@link #updateEntity(DataSet, DataSetInputDTO)} (PUT/PATCH).
   *
   * <p>A {@code null} {@code namedApis} on the input is treated as "no namedApis field in the patch
   * body" — leave the entity's existing collection untouched. An empty list clears the collection
   * (orphan removal triggers).
   */
  @AfterMapping
  default void linkNamedApis(DataSetInputDTO input, @MappingTarget DataSet entity) {
    List<NamedApiInputDTO> incoming = input.getNamedApis();
    if (incoming == null) {
      return;
    }
    Set<NamedApi> mapped = new HashSet<>();
    for (NamedApiInputDTO dto : incoming) {
      mapped.add(toNamedApiEntity(dto));
    }
    entity.setNamedApis(mapped);
  }

  /**
   * Maps a {@link NamedApiInputDTO} to a {@link NamedApi} entity. {@code routeId} stays null on
   * input (saga populates it post-release), {@code dataSet} back-reference is set by {@link
   * DataSet#setNamedApis(java.util.Collection)} when the collection is replaced, audit fields are
   * managed by JPA Auditing.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "modifiedBy", ignore = true)
  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "routeId", ignore = true)
  NamedApi toNamedApiEntity(NamedApiInputDTO dto);

  /** Maps a {@link NamedApi} entity back to its input form (used by PATCH round-tripping). */
  NamedApiInputDTO toNamedApiInputDto(NamedApi entity);

  /**
   * Maps a {@link NamedApi} entity to a {@link NamedApiOutputDTO}. {@code previewUrl} is populated
   * by {@code DataSetAssembler}, not the mapper, since it depends on configuration.
   */
  @Mapping(target = "previewUrl", ignore = true)
  NamedApiOutputDTO toNamedApiOutputDto(NamedApi entity);
}
