package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.datasink.DataSinkConfigurationOutput;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.FrostConfigurationOutput;
import de.civitascore.portal.model.output.PostgisConfigurationOutput;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataSink} entities to {@link DataSinkOutputDTO}. Participates in
 * the template method pattern defined by {@link BaseAssembler}.
 *
 * <p>{@link #enrichDto} resolves the type-specific configuration (read back from the Model Forge
 * registry via the sink's {@code configurationUrn} pin):
 *
 * <ul>
 *   <li>FROST — sets a {@link FrostConfigurationOutput} with the referenced target structure.
 *   <li>POSTGIS — looks up the {@link de.civitascore.portal.model.entity.DataStructureVersion} and
 *       builds a {@link PostgisConfigurationOutput} with the nested summary.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DataSinkAssembler implements BaseAssembler<DataSink, DataSinkOutputDTO, UUID> {

  private final DataSinkMapper dataSinkMapper;
  private final ModelRegistryGateway modelRegistryGateway;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final DataStructureVersionMapper dataStructureVersionMapper;

  /** {@inheritDoc} Delegates to the {@link DataSinkMapper} for basic field mapping. */
  @Override
  public DataSinkOutputDTO mapToBaseDto(DataSink entity) {
    return dataSinkMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Resolves the type-specific {@code configuration} object from the registry-stored
   * configuration document and the derived {@code inUse} flag.
   */
  @Override
  public DataSinkOutputDTO enrichDto(DataSinkOutputDTO dto, DataSink entity) {
    dto.setInUse(entity.getPipeline() != null);

    if (entity.getDataSinkType() == null) {
      return dto;
    }

    dto.setConfiguration(buildConfiguration(entity, fetchConfiguration(entity)));
    dto.setConfigurationUrn(entity.getConfigurationUrn());
    return dto;
  }

  /**
   * {@inheritDoc} Converts a DataSink entity back to its input DTO for PATCH operations. The
   * current configuration is read from the registry (an empty map when none is stored, e.g. FROST
   * sinks, satisfying the input's not-null constraint).
   */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSink entity) {
    DataSinkInputDTO input = dataSinkMapper.toInput(entity);
    Map<String, Object> configuration = fetchConfiguration(entity);
    input.setConfiguration(configuration != null ? configuration : new HashMap<>());
    return (I) input;
  }

  /**
   * Reads the sink's configuration document back from the registry; {@code null} when no
   * configuration is stored (e.g. FROST sinks).
   *
   * <p>The {@code connectionType} discriminator is <b>synthetic</b>: {@link
   * de.civitascore.portal.service.DataSinkService} derives it from {@code dataSinkType} and stamps
   * it into the payload on write so the document satisfies {@code datasink.schema.json}'s {@code
   * oneOf}. It is not user-authored configuration, so it is stripped here — symmetric to the write
   * — leaving {@code configuration} as pure host content. Without this, a PATCH would reconstitute
   * the current configuration (via {@link #toInput}) with {@code connectionType} included, and
   * {@code DataSinkService.validateFrostConfiguration} (which requires an exact {@code {element}}
   * key set) would reject the unchanged FROST config with a 400.
   */
  private Map<String, Object> fetchConfiguration(DataSink entity) {
    if (entity.getConfigurationUrn() == null) {
      return null;
    }
    return modelRegistryGateway
        .fetchPayload(entity.getConfigurationUrn())
        .map(ModelRegistryGateway.RegistryDocument::content)
        .map(
            content -> {
              // Defensive copy: the registry read is mutable, but a stubbed Map.of(...) is not.
              Map<String, Object> hostContent = new HashMap<>(content);
              hostContent.remove("connectionType");
              return hostContent;
            })
        .orElse(null);
  }

  private DataSinkConfigurationOutput buildConfiguration(DataSink entity, Map<String, Object> raw) {
    return switch (entity.getDataSinkType()) {
      case FROST -> buildFrostConfiguration(raw);
      case POSTGIS -> buildPostgisConfiguration(raw);
      default -> null;
    };
  }

  private FrostConfigurationOutput buildFrostConfiguration(Map<String, Object> raw) {
    FrostConfigurationOutput output = new FrostConfigurationOutput();
    if (raw != null && raw.get("element") instanceof String element) {
      output.setElement(element);
    }
    return output;
  }

  private PostgisConfigurationOutput buildPostgisConfiguration(Map<String, Object> raw) {
    PostgisConfigurationOutput output = new PostgisConfigurationOutput();
    if (raw == null) {
      return output;
    }
    output.setTableName((String) raw.get("tableName"));
    if (raw.get("element") instanceof String element) {
      output.setElement(element);
      // The document stores the structure reference as a URN, but the OWS named-API editor
      // needs the version's database ids to fetch it. A dangling URN leaves the summary
      // absent rather than failing every sink listing over one unresolvable reference.
      dataStructureVersionRepository
          .findFirstByModelUrnStartingWith(element)
          .map(dataStructureVersionMapper::toSummary)
          .ifPresent(output::setDataStructureVersion);
    }
    return output;
  }
}
