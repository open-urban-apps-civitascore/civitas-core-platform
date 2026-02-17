package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataStructureVersionService
    extends BaseService<DataStructureVersion, DataStructureVersionInputDTO> {

  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final DataStructureVersionMapper dataStructureVersionMapper;

  private final DataStructureService dataStructureService;
  private final ModelService modelService;

  @Override
  protected DataStructureVersionRepository getRepository() {
    return dataStructureVersionRepository;
  }

  @Override
  protected DataStructureVersionMapper getMapper() {
    return dataStructureVersionMapper;
  }

  @Override
  protected String getEntityName() {
    return DataStructureVersion.class.getSimpleName();
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * DataStructureVersion along with dataStructure in a single JOIN query, preventing N+1 query
   * problems that would occur with lazy loading.
   */
  @Override
  public Optional<DataStructureVersion> findById(UUID id) {
    Optional<DataStructureVersion> entity =
        dataStructureVersionRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  public Optional<String> findModelForDataStructureVersion(DataStructureVersion entity) {
    if (StringUtils.isNotBlank(entity.getModelAtlasUri())) {
      try {
        String modelContent =
            modelService.downloadModel(entity.getModelAtlasUri(), "application/xml");
        return Optional.ofNullable(modelContent);
      } catch (Exception e) {
        // error has already been logged in ModelRestClientRequestService, so just return empty here
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  @Override
  protected DataStructureVersion postConvertToEntity(
      DataStructureVersion entity, DataStructureVersionInputDTO input) {
    // Set dataStructure
    Optional.ofNullable(input.getDataStructureId())
        .map(dataStructureService::findByIdOrThrow)
        .ifPresentOrElse(
            entity::setDataStructure,
            () -> {
              throw new InvalidInputException(
                  "dataStructureId", entity.getId(), "dataStructureId cannot be null or blank");
            });

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataStructureVersionInputDTO preProcessCreateInput(DataStructureVersionInputDTO input) {
    // Set DRAFT status for newly created data structure versions
    input.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

    return super.preProcessCreateInput(input);
  }

  @Override
  protected DataStructureVersion postSave(
      DataStructureVersion entity, DataStructureVersionInputDTO input) {
    if (StringUtils.isNotBlank(input.getModel())
        && StringUtils.isNotBlank(input.getModelAtlasUri())) {
      try {
        modelService.uploadModelString(input.getModel(), input.getModelAtlasUri());
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to upload model to Model Atlas for modelAtlasUri: " + input.getModelAtlasUri(),
            e);
      }
    }

    return super.postSave(entity, input);
  }
}
