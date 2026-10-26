package de.civitascore.portal.util;

import org.jspecify.annotations.Nullable;

/**
 * One answer of portal-backend in an integration test.
 *
 * @param path the request URI, including the context path
 * @param problemType the {@code type} of an {@code application/problem+json} answer, else {@code
 *     null}
 */
public record RecordedAnswer(
    String method, String path, int status, @Nullable String problemType) {}
