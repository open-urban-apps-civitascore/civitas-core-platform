package de.civitascore.portal.problem;

/**
 * One rejected field of a {@link ProblemType#VALIDATION_FAILED} answer, an element of its {@code
 * fieldErrors} member. portal-frontend reads both components by name.
 *
 * @param field the path of the field in the request body
 * @param message what is wrong, without the rejected value
 */
public record FieldError(String field, String message) {}
