package de.civitascore.portal.model.input.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Bean Validation constraint that enforces both the URL-safe slug shape and the reserved-slug
 * blocklist in a single annotation (see {@link NamedApiAllowedSlugValidator#RESERVED}). Length is a
 * separate concern — pair with {@code @Size} at the call site.
 *
 * <p>{@code null} is treated as valid; pair with {@code @NotBlank} for missing-field reporting.
 */
@Documented
@Constraint(validatedBy = NamedApiAllowedSlugValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface NamedApiAllowedSlug {

  String message() default "Slug is invalid or reserved";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
