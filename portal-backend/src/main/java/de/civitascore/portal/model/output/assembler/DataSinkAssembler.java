package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.datasink.DataSinkConfigurationOutput;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.FrostConfigurationOutput;
import de.civitascore.portal.model.output.PostgisConfigurationOutput;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataSink} entities to {@link DataSinkOutputDTO}. Participates in
 * the template method pattern defined by {@link BaseAssembler}.
 *
 * <p>{@link #enrichDto} resolves the type-specific configuration:
 *
 * <ul>
 *   <li>FROST — sets an empty {@link FrostConfigurationOutput}.
 *   <li>POSTGIS — looks up the {@link de.civitascore.portal.model.entity.DataStructureVersion} and
 *       builds a {@link PostgisConfigurationOutput} with the nested summary.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DataSinkAssembler implements BaseAssembler<DataSink, DataSinkOutputDTO, UUID> {

  private final DataSinkMapper dataSinkMapper;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final DataStructureVersionMapper dataStructureVersionMapper;

  /** {@inheritDoc} Delegates to the {@link DataSinkMapper} for basic field mapping. */
  @Override
  public DataSinkOutputDTO mapToBaseDto(DataSink entity) {
    return dataSinkMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Resolves the type-specific {@code configuration} object from the raw JSONB map
   * and the derived {@code inUse} flag.
   */
  @Override
  public DataSinkOutputDTO enrichDto(DataSinkOutputDTO dto, DataSink entity) {
    dto.setInUse(entity.getPipeline() != null);

    if (entity.getDataSinkType() == null) {
      return dto;
    }

    dto.setConfiguration(buildConfiguration(entity.getDataSinkType(), entity.getConfiguration()));
    return dto;
  }

  /** {@inheritDoc} Converts a DataSink entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSink entity) {
    return (I) dataSinkMapper.toInput(entity);
  }

  private DataSinkConfigurationOutput buildConfiguration(
      DataSinkType type, Map<String, Object> raw) {
    return switch (type) {
      case FROST -> new FrostConfigurationOutput();
      case POSTGIS -> buildPostgisConfiguration(raw);
      default -> null;
    };
  }

  private PostgisConfigurationOutput buildPostgisConfiguration(Map<String, Object> raw) {
    PostgisConfigurationOutput output = new PostgisConfigurationOutput();

    if (raw == null) {
      return output;
    }

    output.setTableName((String) raw.get("tableName"));

    Object dsvIdRaw = raw.get("dataStructureVersionId");
    if (dsvIdRaw != null) {
      try {
        UUID dsvId = UUID.fromString(dsvIdRaw.toString());
        dataStructureVersionRepository
            .findById(dsvId)
            .ifPresent(
                dsv -> output.setDataStructureVersion(dataStructureVersionMapper.toSummary(dsv)));
      } catch (IllegalArgumentException ignored) {
        // keep dataStructureVersion unset for malformed persisted config
      }
    }

    return output;
  }
}
