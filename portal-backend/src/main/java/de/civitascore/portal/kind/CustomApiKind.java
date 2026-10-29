package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;

/** A free-form API that no Data sink of the platform backs. */
@Component
public class CustomApiKind implements ApiKind {

  @Override
  public ApiStandard id() {
    return ApiStandard.CUSTOM;
  }

  @Override
  public Set<DataSinkType> backingDataSinkKinds() {
    return Set.of();
  }

  @Override
  public OptionalInt maxPerDataSet() {
    return OptionalInt.empty();
  }

  @Override
  public boolean publishesLayers() {
    return false;
  }
}
