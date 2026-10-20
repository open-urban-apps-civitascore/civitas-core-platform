package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DashboardMapper;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.output.DashboardOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DashboardAssembler implements BaseAssembler<Dashboard, DashboardOutputDTO, UUID> {

  private final DashboardMapper dashboardMapper;
  private final ModelRegistryGateway modelRegistryGateway;

  @Override
  public DashboardOutputDTO mapToBaseDto(Dashboard entity) {
    DashboardOutputDTO output = dashboardMapper.toOutput(entity);
    if (entity.getModelUrn() == null) {
      return output;
    }
    Map<String, Object> model =
        modelRegistryGateway.fetchPayload(entity.getModelUrn())
            .map(ModelRegistryGateway.RegistryDocument::content)
            .orElse(null);
    if (model == null) {
      return output;
    }
    if (model.get("title") instanceof String title) output.setName(title);
    if (model.containsKey("description")) {
      output.setDescription((String) model.get("description"));
    }
    if (model.get("released") instanceof Boolean released) output.setReleased(released);
    if (model.get("standaloneMode") instanceof Number mode) {
      output.setStandaloneMode(mode.intValue());
    }
    if (model.get("showFilters") instanceof Boolean showFilters) {
      output.setShowFilters(showFilters);
    }
    if (model.get("filterExpansion") instanceof String expansion) {
      output.setFilterExpansion(
          de.civitascore.portal.model.embedded.DashboardFilterExpansion.valueOf(expansion));
    }
    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Dashboard entity) {
    return (I) dashboardMapper.toInput(entity);
  }
}
