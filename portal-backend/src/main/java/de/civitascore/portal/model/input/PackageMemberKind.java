package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The kind of a package member, in the vocabulary of the artifact-type segment of a CORE URN. The
 * install accepts the kinds that this enum lists.
 */
public enum PackageMemberKind {
  @JsonProperty("datastructure")
  DATASTRUCTURE
}
