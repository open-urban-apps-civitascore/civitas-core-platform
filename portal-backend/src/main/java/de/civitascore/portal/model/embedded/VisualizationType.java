package de.civitascore.portal.model.embedded;

/**
 * Wire-level discriminator for the {@code VisualizationDTO} union. Lives at the DTO layer only —
 * the visualization kinds are too different to share a persistence base, so there is no entity
 * hierarchy and no {@code visualization_type} column behind it. {@code DASHBOARD} is the only kind
 * today; map / report are foreseen additive members.
 */
public enum VisualizationType {
  DASHBOARD
}
