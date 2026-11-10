package de.civitascore.portal.util;

/** Thrown when a dashboard cannot be exported yet, because Superset does not hold it. */
public class DashboardNotExportableException extends RuntimeException {
  public DashboardNotExportableException(String message) {
    super(message);
  }
}
