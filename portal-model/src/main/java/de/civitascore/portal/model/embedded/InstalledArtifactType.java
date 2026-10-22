package de.civitascore.portal.model.embedded;

/**
 * The type of an artifact that an install records. The column stores the name as text, so a new
 * type needs no migration.
 */
public enum InstalledArtifactType {
  DATA_STRUCTURE,
  DATA_SOURCE,
  DATA_SET,
  NAMED_API,
  MAPPING,
  DATA_SINK,
  PIPELINE,
  STYLE,
  LAYER
}
