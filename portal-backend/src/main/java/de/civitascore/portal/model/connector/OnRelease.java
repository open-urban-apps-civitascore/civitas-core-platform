package de.civitascore.portal.model.connector;

/**
 * Validation group for constraints that only apply when releasing a data source. Required-ness
 * checks (e.g. {@code @NotEmpty}, {@code @NotBlank}) use this group so that drafts can be saved
 * with incomplete configuration.
 */
public interface OnRelease {}
