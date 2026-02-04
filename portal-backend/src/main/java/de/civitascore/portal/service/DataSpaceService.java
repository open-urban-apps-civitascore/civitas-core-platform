package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.DataSpaceMapper;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DataSpaceService extends BaseService<DataSpace, DataSpaceInputDTO> {

  private final DataSpaceRepository dataSpaceRepository;
  private final DataSpaceMapper dataSpaceMapper;
  private final UserService userService;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<AllowedScopes> allowedScopesProvider;

  public DataSpaceService(
      DataSpaceRepository dataSpaceRepository,
      DataSpaceMapper dataSpaceMapper,
      UserService userService,
      ObjectMapper objectMapper,
      ObjectProvider<AllowedScopes> allowedScopesProvider) {
    this.dataSpaceRepository = dataSpaceRepository;
    this.dataSpaceMapper = dataSpaceMapper;
    this.userService = userService;
    this.objectMapper = objectMapper;
    this.allowedScopesProvider = allowedScopesProvider;
  }

  @Override
  protected DataSpaceRepository getRepository() {
    return dataSpaceRepository;
  }

  @Override
  protected DataSpaceMapper getMapper() {
    return dataSpaceMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSpace.class.getSimpleName();
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * DataSpace along with owner and parentDataSpace in a single JOIN query, preventing N+1 query
   * problems that would occur with lazy loading.
   */
  @Override
  public Optional<DataSpace> findById(UUID id) {
    Optional<DataSpace> entity = dataSpaceRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  @Override
  protected DataSpace postConvertToEntity(DataSpace entity, DataSpaceInputDTO input) {
    // Use getReferenceById for ManyToOne relationships to avoid unnecessary SELECT queries
    if (input.getOwnerUserId() != null) {
      entity.setOwner(userService.findByIdOrThrow(input.getOwnerUserId()));
    } else {
      entity.setOwner(null);
    }

    if (input.getParentDataSpaceId() != null) {
      entity.setParentDataSpace(findByIdOrThrow(input.getParentDataSpaceId()));
    } else {
      entity.setParentDataSpace(null);
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSpace preSave(DataSpace entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(DataSpace entity) {
    dataSpaceRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    DataSpace.class.getSimpleName(), "name", entity.getName());
              }
            });
  }

  @Override
  protected DataSpaceInputDTO preProcessUpdateInput(
      DataSpaceInputDTO input, DataSpace existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("name") && StringUtils.isBlank(jsonNode.get("name").asText())) {
        throw new InvalidInputException(
            "name", existingEntity.getId(), "Name cannot be null or blank");
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  /**
   * Apply scope-based filtering for collection queries (M5.5).
   *
   * <p>Filters dataspaces to only those the user is authorized to access. Wildcard (*) or inactive
   * scope filtering bypasses the filter.
   */
  @Override
  protected Specification<DataSpace> preProcessQuery(
      Specification<DataSpace> spec, Pageable pageable) {
    AllowedScopes scopes = allowedScopesProvider.getObject();

    if (!scopes.isActive() || scopes.isWildcard()) {
      // No filtering: direct backend access or TENANT scope
      return spec;
    }

    log.debug("Filtering dataspaces by {} allowed IDs", scopes.getScopeIds().size());
    Specification<DataSpace> scopeFilter =
        ScopeFilteringSpecification.dataSpaceById(scopes.getScopeIds());

    return spec == null ? scopeFilter : spec.and(scopeFilter);
  }
}
