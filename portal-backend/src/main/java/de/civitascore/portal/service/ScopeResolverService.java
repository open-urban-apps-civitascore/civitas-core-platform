package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScopeResolverService {

  private final DataSetRepository dataSetRepository;
  private final DataSourceRepository dataSourceRepository;
  private final DataStructureRepository dataStructureRepository;

  public Assignment resolveScope(Assignment entity, ScopeType scopeType, UUID scopeId) {
    if (scopeId == null) {
      return entity;
    }

    switch (scopeType) {
      case DATASET ->
          entity.setDataset(
              dataSetRepository
                  .findById(scopeId)
                  .orElseThrow(() -> new ResourceNotFoundException("DataSet", scopeId)));
      case DATASPACE, CATALOG ->
          throw new InvalidInputException(
              "Assignment",
              scopeType.name(),
              scopeType + " scope is not available in this release");
      case DATASOURCE ->
          entity.setDataSource(
              dataSourceRepository
                  .findById(scopeId)
                  .orElseThrow(() -> new ResourceNotFoundException("DataSource", scopeId)));
      case DATASTRUCTURE ->
          entity.setDataStructure(
              dataStructureRepository
                  .findById(scopeId)
                  .orElseThrow(() -> new ResourceNotFoundException("DataStructure", scopeId)));
      default -> {}
    }

    return entity;
  }
}
