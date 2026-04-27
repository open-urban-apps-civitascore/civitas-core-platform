package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.NamedApiOutputDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
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

  /**
   * {@inheritDoc} Used by {@link
   * de.civitascore.portal.controller.BaseController#patch(java.util.UUID,
   * com.fasterxml.jackson.databind.JsonNode)} to build the "current state" DTO that the JSON-merge
   * patch body is applied on top of. The {@link DataSetInputDTO#getNamedApis() namedApis} field is
   * forced to {@code null} (see {@link #nullOutNamedApisAfterToInput}) so PATCH semantics work
   * correctly: {@link #linkNamedApis(DataSetInputDTO, DataSet)} treats null as "no namedApis field
   * in the patch body, leave the entity untouched". If we round-tripped the existing collection
   * here, a PATCH that omits {@code namedApis} would resurface it through the merge and then
   * recreate every entry with fresh ids — losing {@code routeId}, audit metadata, and breaking any
   * FK relationships.
   */
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "namedApis", ignore = true)
  @Override
  DataSetInputDTO toInput(DataSet entity);

  /** Overrides the {@code DataSetInputDTO.namedApis} field default of {@code new ArrayList<>()}. */
  @AfterMapping
  default void nullOutNamedApisAfterToInput(@MappingTarget DataSetInputDTO dto) {
    dto.setNamedApis(null);
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
  // Routed through linkNamedApis (below) so PATCH null-vs-empty semantics are applied
  // uniformly with toEntity. MapStruct would otherwise call entity.setNamedApis(mappedSet)
  // here directly, which would clear the collection on every PATCH whose currentDto carries a
  // (non-null) round-tripped list — see #1315 PATCH bug fix in nullOutNamedApisAfterToInput.
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
