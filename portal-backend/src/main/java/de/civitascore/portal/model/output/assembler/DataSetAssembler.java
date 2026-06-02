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

  @Override
  public DataSetOutputDTO mapToBaseDto(DataSet entity) {
    return dataSetMapper.toOutput(entity);
  }

  @Override
  public DataSetOutputDTO enrichDto(DataSetOutputDTO dto, DataSet entity) {
    if (entity.getCreatedBy() != null) {
      userRepository
          .findByExternalId(entity.getCreatedBy().toString())
          .ifPresent(user -> dto.setCreatedBy(userMapper.toSummary(user)));
    }
    if (entity.getId() != null) {
      // Prefer the public URL the saga actually provisioned the APISIX route under
      // (APISIX_API_PUBLIC_URL → dataSet.publicUrl). The backend-local civitas.api.base-url is only
      // a fallback for datasets that predate the saga (publicUrl still null); using it as the
      // primary source produces broken preview/discovery links whenever the two configs diverge.
      String datasetUrl =
          entity.getPublicUrl() != null
              ? entity.getPublicUrl()
              : civitasProperties.api().baseUrl() + "/v1/datasets/" + entity.getId();
      dto.getNamedApis().forEach(api -> api.setPreviewUrl(datasetUrl + "/" + api.getSlug()));
    }
    return dto;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSet entity) {
    return (I) dataSetMapper.toInput(entity);
  }
}
