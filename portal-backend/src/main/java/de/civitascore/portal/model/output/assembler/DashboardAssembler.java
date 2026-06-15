package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DashboardMapper;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.output.DashboardOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DashboardAssembler implements BaseAssembler<Dashboard, DashboardOutputDTO, UUID> {

  private final DashboardMapper dashboardMapper;

  @Override
  public DashboardOutputDTO mapToBaseDto(Dashboard entity) {
    return dashboardMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Dashboard entity) {
    return (I) dashboardMapper.toInput(entity);
  }
}
