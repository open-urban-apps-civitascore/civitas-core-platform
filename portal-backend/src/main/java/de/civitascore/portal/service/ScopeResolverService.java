package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScopeResolverService {

  private final CatalogRepository catalogRepository;
  private final DataSetRepository dataSetRepository;
  private final DataSpaceRepository dataSpaceRepository;
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
      case DATASPACE ->
          entity.setDataSpace(
              dataSpaceRepository
                  .findById(scopeId)
                  .orElseThrow(() -> new ResourceNotFoundException("DataSpace", scopeId)));
      case CATALOG ->
          entity.setCatalog(
              catalogRepository
                  .findById(scopeId)
                  .orElseThrow(() -> new ResourceNotFoundException("Catalog", scopeId)));
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
