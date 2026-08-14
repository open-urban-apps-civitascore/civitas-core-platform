package de.civitascore.portal.model.embedded;

/**
 * What a bundle install did with one contained artifact. Mirrors the {@code action} values of the
 * import response; recorded because reference counting on uninstall depends on it — a REUSED
 * artifact was not brought by this bundle and must never be removed with it.
 */
public enum InstalledArtifactAction {
  /** Newly created by this install. */
  CREATED,

  /** Already installed with identical content; this install only linked it. */
  REUSED
}
