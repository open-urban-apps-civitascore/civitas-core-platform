package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import java.util.OptionalInt;
import java.util.Set;

/** What the platform knows about one kind of API. One Spring bean for each kind. */
public interface ApiKind {

  /** The persisted id of this kind. */
  ApiStandard id();

  /**
   * The Data sink kinds that can hold the data of an API of this kind. An API of this kind exists
   * only while the Dataset has an active Data sink of one of them. Empty for a kind that no Data
   * sink backs: such an API is neither required to have a Data sink nor removed with one.
   */
  Set<DataSinkType> backingDataSinkKinds();

  /**
   * Whether an API of this kind can exist while the Dataset has active Data sinks of these kinds.
   */
  default boolean isBackedBy(Set<DataSinkType> activeDataSinkKinds) {
    return backingDataSinkKinds().isEmpty()
        || backingDataSinkKinds().stream().anyMatch(activeDataSinkKinds::contains);
  }

  /** How many APIs of this kind one Dataset can have. Empty for no limit. */
  OptionalInt maxPerDataSet();

  /** Whether an API of this kind publishes the Layers of its Dataset. */
  boolean publishesLayers();
}
