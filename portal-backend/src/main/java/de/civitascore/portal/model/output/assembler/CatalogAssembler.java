package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.CatalogMapper;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.input.CatalogInputDTO;
import de.civitascore.portal.model.output.CatalogOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogAssembler implements BaseAssembler<Catalog, CatalogOutputDTO, UUID> {
  private final CatalogMapper catalogMapper;
  private final DataSetMapper dataSetMapper;

  @Override
  public CatalogOutputDTO mapToBaseDto(Catalog entity) {
    CatalogOutputDTO output = catalogMapper.toOutput(entity);

    entity.getChildCatalogs().stream()
        .map(catalogMapper::toSummary)
        .forEach(output.getChildCatalogs()::add);

    entity.getParentCatalogs().stream()
        .map(catalogMapper::toSummary)
        .forEach(output.getParentCatalogs()::add);

    entity.getDataSets().stream().map(dataSetMapper::toSummary).forEach(output.getDataSets()::add);

    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public CatalogInputDTO toInput(Catalog entity) {
    return catalogMapper.toInput(entity);
  }
}
