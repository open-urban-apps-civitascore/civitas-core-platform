package de.civitascore.modelforge.persistence.postgres;

/**
 * One stored version of an artifact, reduced to what deciding a reference's resolvability needs:
 * the artifact's logical identity, which version it currently points at, and this row's version.
 *
 * <p>Content, formats and timestamps are deliberately absent — a bulk existence answer reads no
 * representation.
 *
 * @param logicalUrn the artifact's version-free identity
 * @param currentVersion the version a logical or {@code :latest} reference to it resolves to
 * @param version this row's own version
 */
record ArtifactVersionIdentity(String logicalUrn, String currentVersion, String version) {}
