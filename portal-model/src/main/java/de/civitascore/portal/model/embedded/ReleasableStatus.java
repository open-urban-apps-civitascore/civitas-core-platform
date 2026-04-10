package de.civitascore.portal.model.embedded;

/** Common interface for status enums that support a release lifecycle (DRAFT → AVAILABLE). */
public interface ReleasableStatus {
  boolean isDraft();

  boolean isAvailable();
}
