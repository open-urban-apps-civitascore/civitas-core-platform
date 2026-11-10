package de.civitascore.portal.util;

/** Thrown when too many dashboard exports wait on this backend instance. */
public class DashboardExportBusyException extends RuntimeException {
  public DashboardExportBusyException(String message) {
    super(message);
  }
}
