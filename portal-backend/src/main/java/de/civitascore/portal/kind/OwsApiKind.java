package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * OGC web services over the Dataset's PostGIS tables. At most one, because its GeoServer workspace
 * and route both derive from the Dataset id: a second one would serve the same Layers.
 */
@Component
public class OwsApiKind implements ApiKind {

  @Override
  public ApiStandard id() {
    return ApiStandard.OWS;
  }

  @Override
  public Set<DataSinkType> backingDataSinkKinds() {
    return Set.of(DataSinkType.POSTGIS);
  }

  @Override
  public OptionalInt maxPerDataSet() {
    return OptionalInt.of(1);
  }

  @Override
  public boolean publishesLayers() {
    return true;
  }
}
