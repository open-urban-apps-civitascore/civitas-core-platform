package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.configuration.CivitasProperties;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataSet} entities to {@link DataSetOutputDTO}. Participates in
 * the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class DataSetAssembler implements BaseAssembler<DataSet, DataSetOutputDTO, UUID> {

  private final DataSetMapper dataSetMapper;
  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final CivitasProperties civitasProperties;

  /** {@inheritDoc} Delegates to the {@link DataSetMapper} for basic field mapping. */
  @Override
  public DataSetOutputDTO mapToBaseDto(DataSet entity) {
    return dataSetMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Enriches the output with: (a) the creating user's summary resolved from the audit
   * trail; (b) per-named-API {@code previewUrl} built from the configured data-plane domain (per
   * concept #1380 + ADR #1387), in the form {@code
   * https://{civitas.api.domain}/v1/datasets/{datasetId}/{slug}}. Preview URLs are absent when the
   * dataset has not yet been persisted (no id).
   */
  @Override
  public DataSetOutputDTO enrichDto(DataSetOutputDTO dto, DataSet entity) {
    if (entity.getCreatedBy() != null) {
      userRepository
          .findByExternalId(entity.getCreatedBy().toString())
          .ifPresent(user -> dto.setCreatedBy(userMapper.toSummary(user)));
    }
    if (entity.getId() != null && dto.getNamedApis() != null) {
      String baseUrl =
          "https://" + civitasProperties.api().domain() + "/v1/datasets/" + entity.getId();
      dto.getNamedApis().forEach(api -> api.setPreviewUrl(baseUrl + "/" + api.getSlug()));
    }
    return dto;
  }

  /** {@inheritDoc} Converts a dataset entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSet entity) {
    return (I) dataSetMapper.toInput(entity);
  }
}
