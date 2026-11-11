package de.civitascore.portal.repository.specification.base;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a query parameter of the {@code @Spec} annotations on the same specification interface
 * for the OpenAPI description. Every query parameter of a specification needs one.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(FilterParams.class)
public @interface FilterParam {

  String name();

  String description();

  /**
   * The type of the parameter value, for a specification that does not compare it with the entity
   * attribute at its path. {@code Void} takes the type of that attribute.
   */
  Class<?> type() default Void.class;
}
