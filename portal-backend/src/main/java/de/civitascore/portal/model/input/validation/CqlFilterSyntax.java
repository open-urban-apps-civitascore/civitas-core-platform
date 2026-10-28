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
 * Bean Validation constraint that a CQL filter parses as ECQL the way GeoServer parses it. Only the
 * syntax is checked: column and function names are resolved by GeoServer once the layer is
 * published. Length is a separate concern — pair with {@code @Size} at the call site.
 *
 * <p>{@code null} and blank are valid: GeoServer then applies no filter.
 */
@Documented
@Constraint(validatedBy = CqlFilterSyntaxValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface CqlFilterSyntax {

  String message() default "CQL filter is not valid ECQL";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
