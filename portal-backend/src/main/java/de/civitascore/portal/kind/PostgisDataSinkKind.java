package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.DataSinkType;
import org.springframework.stereotype.Component;

/** Data sink kind that writes into a PostGIS table. */
@Component
public class PostgisDataSinkKind implements DataSinkKind {

  @Override
  public DataSinkType id() {
    return DataSinkType.POSTGIS;
  }
}
