package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.NamedApi;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** What each {@link ApiStandard} requires of its Dataset. */
public final class NamedApiRules {

  private NamedApiRules() {}

  /**
   * The Data sink types that can hold the data of an API of this standard. An API of this standard
   * exists only while the Dataset has an active Data sink of one of them. Empty for a standard that
   * no Data sink backs: such an API is neither required to have a Data sink nor removed with one.
   */
  public static Set<DataSinkType> backingDataSinkTypes(ApiStandard standard) {
    return switch (standard) {
      case OWS -> Set.of(DataSinkType.POSTGIS);
      case STA -> Set.of(DataSinkType.FROST);
      case CUSTOM -> Set.of();
    };
  }

  /**
   * Whether an API of this standard can exist while the Dataset has active Data sinks of these
   * types.
   */
  public static boolean isBackedBy(ApiStandard standard, Set<DataSinkType> activeDataSinkTypes) {
    Set<DataSinkType> backing = backingDataSinkTypes(standard);
    return backing.isEmpty() || backing.stream().anyMatch(activeDataSinkTypes::contains);
  }

  /** How many APIs of this standard one Dataset can have. Empty for no limit. */
  public static OptionalInt maxPerDataSet(ApiStandard standard) {
    return switch (standard) {
      // The GeoServer workspace and route both derive from the Dataset id: a second OWS API would
      // serve the same Layers. All FROST Data sinks of a Dataset share one project.
      case OWS, STA -> OptionalInt.of(1);
      case CUSTOM -> OptionalInt.empty();
    };
  }

  /** The API among the given ones that the Layers of their Dataset link to. */
  public static Optional<NamedApi> layerPublishingApi(Collection<NamedApi> apis) {
    return apis.stream().filter(api -> api.getStandard() == ApiStandard.OWS).findFirst();
  }
}
