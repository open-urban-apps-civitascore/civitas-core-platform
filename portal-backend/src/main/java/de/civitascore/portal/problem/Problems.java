package de.civitascore.portal.problem;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The problem types a handler method answers beyond the ones every operation can answer. The
 * OpenAPI description documents the error responses of the operation from them, so the method
 * declares no error {@code @ApiResponse}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Problems {

  ProblemType[] value();
}
