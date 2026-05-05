package de.civitascore.portal.model.embedded;

/** Common interface for status enums that support a release lifecycle (DRAFT → AVAILABLE). */
public interface ReleasableStatus {
  /** Returns the name of the enum constant (provided by {@link Enum#name()}). */
  String name();

  default boolean isDraft() {
    return "DRAFT".equals(name());
  }

  default boolean isAvailable() {
    return "AVAILABLE".equals(name());
  }
}
