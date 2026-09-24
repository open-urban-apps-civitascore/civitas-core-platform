package de.civitascore.portal.repository;

/**
 * Whether the host row behind a registry referrer counts as released.
 *
 * @param logicalUrn the logical CORE URN the row is found by
 * @param released whether the row, or the Dataset owning it, is AVAILABLE
 */
public record ReferrerReleaseState(String logicalUrn, boolean released) {}
