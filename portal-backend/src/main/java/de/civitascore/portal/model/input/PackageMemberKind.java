package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The kind of one package member, in the same vocabulary the artifact-type segment of a CORE URN
 * uses. Declared explicitly rather than derived from the member's URN so the manifest stays
 * readable, sortable and checkable without parsing any member's content — the same reason the
 * artifact envelope carries an {@code artifactType} next to its {@code artifactId}.
 */
public enum PackageMemberKind {
  @JsonProperty("datastructure")
  DATASTRUCTURE,

  @JsonProperty("datasource")
  DATASOURCE,

  @JsonProperty("datasink")
  DATASINK,

  @JsonProperty("mapping")
  MAPPING,

  @JsonProperty("pipeline")
  PIPELINE,

  @JsonProperty("dataset")
  DATASET
}
