package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The kind of a package member, in the vocabulary of the artifact-type segment of a CORE URN. The
 * install accepts the kinds that this enum lists.
 *
 * <p>The order of the constants is the order of the install. A member references only members of
 * the kinds before its own kind, so the copy of each member that it references exists before the
 * install needs it.
 */
public enum PackageMemberKind {
  @JsonProperty("datastructure")
  DATASTRUCTURE,
  @JsonProperty("datasource")
  DATASOURCE,
  @JsonProperty("dataset")
  DATASET,
  @JsonProperty("api")
  API,
  @JsonProperty("mapping")
  MAPPING,
  @JsonProperty("datasink")
  DATASINK,
  @JsonProperty("pipeline")
  PIPELINE,
  @JsonProperty("style")
  STYLE,
  @JsonProperty("layer")
  LAYER
}
