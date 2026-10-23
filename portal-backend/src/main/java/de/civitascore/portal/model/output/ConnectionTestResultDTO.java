package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer of a connection test. A source that does not answer as configured is a result with
 * {@code success = false}, not an error response.
 */
@Schema(
    description =
        "The answer of a connection test. A source that does not answer as configured is a result"
            + " with success false, not an error response.")
public record ConnectionTestResultDTO(
    @Schema(description = "Whether the source answered as configured.", example = "true")
        boolean success,
    @Schema(
            description =
                "Why the test failed, null on success: INVALID_CONNECTOR the values do not make a"
                    + " runnable source, SOURCE_ERROR the source answered with an error or could"
                    + " not be reached, TIMEOUT the source did not answer in time.",
            allowableValues = {"INVALID_CONNECTOR", "SOURCE_ERROR", "TIMEOUT"},
            example = "SOURCE_ERROR")
        String reason,
    @Schema(
            description = "What happened, at most 500 characters.",
            example = "FATAL: password authentication failed for user \"reader\"")
        String message,
    @Schema(description = "Number of rows read, SQL only.", example = "5") Integer rowCount,
    @Schema(
            description = "HTTP status code of the call, when the source answered with one.",
            example = "200")
        Integer httpStatus) {}
