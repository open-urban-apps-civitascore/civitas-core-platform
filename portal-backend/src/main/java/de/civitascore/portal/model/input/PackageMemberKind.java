package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The kind of one package member, in the same vocabulary the artifact-type segment of a CORE URN
 * uses. Declared explicitly rather than derived from the member's URN so the manifest stays
 * readable, sortable and checkable without parsing any member's content.
 *
 * <p>The constants are declared in install order, and the install sorts members by it: a data
 * source references a data structure, a mapping references structures and belongs to a dataset, a
 * sink and a pipeline hang off a dataset. References between kinds only ever point at kinds
 * declared earlier, so this order is the dependency order and no per-package sort is needed.
 */
public enum PackageMemberKind {
  @JsonProperty("datastructure")
  DATASTRUCTURE,

  @JsonProperty("datasource")
  DATASOURCE,

  @JsonProperty("dataset")
  DATASET,

  @JsonProperty("mapping")
  MAPPING,

  @JsonProperty("datasink")
  DATASINK,

  @JsonProperty("pipeline")
  PIPELINE
}
