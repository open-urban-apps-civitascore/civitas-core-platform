package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.DataSinkType;
import org.springframework.stereotype.Component;

/** Data sink kind that writes into a FROST SensorThings server. */
@Component
public class FrostDataSinkKind implements DataSinkKind {

  @Override
  public DataSinkType id() {
    return DataSinkType.FROST;
  }
}
