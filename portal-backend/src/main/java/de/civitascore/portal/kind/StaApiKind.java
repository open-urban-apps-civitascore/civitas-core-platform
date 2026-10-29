package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * OGC SensorThings API over the Dataset's FROST project. At most one, because all FROST Data sinks
 * of a Dataset share one project.
 */
@Component
public class StaApiKind implements ApiKind {

  @Override
  public ApiStandard id() {
    return ApiStandard.STA;
  }

  @Override
  public Set<DataSinkType> backingDataSinkKinds() {
    return Set.of(DataSinkType.FROST);
  }

  @Override
  public OptionalInt maxPerDataSet() {
    return OptionalInt.of(1);
  }

  @Override
  public boolean publishesLayers() {
    return false;
  }
}
