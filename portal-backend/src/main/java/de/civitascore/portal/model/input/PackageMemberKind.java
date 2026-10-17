package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The kind of one package member, in the same vocabulary the artifact-type segment of a CORE URN
 * uses. Declared explicitly rather than derived from the member's URN so the manifest stays
 * readable, sortable and checkable without parsing any member's content.
 *
 * <p>The constants are declared in install order, and the install sorts members by it: a data
 * source references a data structure, a mapping references structures and belongs to a dataset, a
 * sink and a pipeline hang off a dataset, a layer publishes a sink with its styles. References
 * between kinds only ever point at kinds declared earlier, so this order is the dependency order
 * and no per-package sort is needed.
 */
public enum PackageMemberKind {
  @JsonProperty("datastructure")
  DATASTRUCTURE,

  @JsonProperty("datasource")
  DATASOURCE,

  @JsonProperty("dataset")
  DATASET,

  /**
   * A named API of a dataset. Installed together with its dataset, not on its own: the dataset
   * reconciles its APIs as one list, so the install hands them over when it creates the dataset.
   */
  @JsonProperty("api")
  API,

  @JsonProperty("mapping")
  MAPPING,

  @JsonProperty("datasink")
  DATASINK,

  @JsonProperty("pipeline")
  PIPELINE,

  /** An SLD style of a dataset, referenced by its layers as default or alternative style. */
  @JsonProperty("style")
  STYLE,

  /** A map layer of a dataset: publishes one data sink, with its styles, through the OWS API. */
  @JsonProperty("layer")
  LAYER
}
