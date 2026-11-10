package de.civitascore.portal.util;

/**
 * Thrown when a dashboard holds what must not be shared. The message comes from config-adapter and
 * names only parts of the caller's own dashboard, never a secret or a platform internal.
 */
public class DashboardExportRefusedException extends RuntimeException {
  public DashboardExportRefusedException(String message) {
    super(message);
  }
}
