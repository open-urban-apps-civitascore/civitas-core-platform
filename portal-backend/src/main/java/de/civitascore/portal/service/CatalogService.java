package de.civitascore.portal.service;

import de.civitascore.portal.mapper.CatalogMapper;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.input.CatalogInputDTO;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import java.util.HashSet;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Catalog} entities. Resolves child catalog and dataset references
 * during entity conversion.
 */
@Service
@RequiredArgsConstructor
public class CatalogService extends BaseService<Catalog, CatalogInputDTO> {

  private final CatalogRepository catalogRepository;
  private final CatalogMapper catalogMapper;
  private final DataSetRepository dataSetRepository;

  @Override
  protected CatalogRepository getRepository() {
    return catalogRepository;
  }

  @Override
  protected CatalogMapper getMapper() {
    return catalogMapper;
  }

  @Override
  protected String getEntityName() {
    return Catalog.class.getSimpleName();
  }

  /**
   * Resolves child catalog and dataset references after DTO-to-entity conversion.
   *
   * @param entity the catalog entity
   * @param input the catalog input DTO containing child catalog and dataset IDs
   * @return the entity with resolved child and dataset relationships
   */
  @Override
  protected Catalog postConvertToEntity(Catalog entity, CatalogInputDTO input) {
    // Set child catalogs
    Optional.ofNullable(input.getChildCatalogIds())
        .map(catalogRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setChildCatalogs);

    // Set data sets
    Optional.ofNullable(input.getDataSetIds())
        .map(dataSetRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setDataSets);

    return super.postConvertToEntity(entity, input);
  }
}
