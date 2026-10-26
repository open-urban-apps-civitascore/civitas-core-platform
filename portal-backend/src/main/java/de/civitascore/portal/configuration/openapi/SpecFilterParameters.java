package de.civitascore.portal.configuration.openapi;

import de.civitascore.portal.repository.specification.base.FilterParam;
import de.civitascore.portal.repository.specification.base.LikeConcatenated;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kaczmarzyk.spring.data.jpa.domain.WithoutTypeConversion;
import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Conjunction;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Disjunction;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * The query parameters that a specification interface of specification-arg-resolver reads, typed
 * from the entity attribute each {@link Spec} compares and described by the {@link FilterParam} on
 * the same interface.
 */
public final class SpecFilterParameters {

  private static final Set<Class<?>> DATE_TIME_TYPES =
      Set.of(Instant.class, OffsetDateTime.class, LocalDateTime.class, ZonedDateTime.class);
  private static final Set<Class<?>> INTEGER_TYPES = Set.of(Integer.class, Long.class, Short.class);

  private SpecFilterParameters() {}

  /**
   * @param specification a specification interface whose generic argument is the entity
   * @return one query parameter per distinct parameter name of its {@link Spec} annotations and
   *     those of its superinterfaces
   * @throws IllegalStateException if a parameter has no {@link FilterParam} on its interface, or if
   *     a path names no attribute of the entity
   */
  public static List<Parameter> of(Class<?> specification) {
    Class<?> entity =
        ResolvableType.forClass(specification).as(Specification.class).getGeneric(0).resolve();
    Map<String, Parameter> parameters = new LinkedHashMap<>();
    for (Class<?> specInterface : specInterfaces(specification)) {
      for (Spec spec : specsOn(specInterface)) {
        for (String name : spec.params()) {
          parameters.computeIfAbsent(
              name, _ -> parameter(name, filterParam(specInterface, name), spec, entity));
        }
      }
    }
    return List.copyOf(parameters.values());
  }

  private static Parameter parameter(
      String name, FilterParam filterParam, Spec spec, Class<?> entity) {
    Class<?> type = filterParam.type() != Void.class ? filterParam.type() : valueType(spec, entity);
    Schema<Object> schema = schemaOf(type);
    if (spec.defaultVal().length > 0) {
      schema.setDefault(typed(spec.defaultVal()[0], type));
    }
    Parameter parameter =
        new Parameter().in("query").name(name).description(filterParam.description());
    if (spec.paramSeparator() != 0 && !comparesText(spec)) {
      Schema<Object> array = new Schema<>().types(Set.of("array"));
      array.setItems(schema);
      return parameter.schema(array).style(Parameter.StyleEnum.FORM).explode(false);
    }
    return parameter.schema(schema);
  }

  private static FilterParam filterParam(Class<?> specInterface, String name) {
    return Arrays.stream(specInterface.getAnnotationsByType(FilterParam.class))
        .filter(filterParam -> filterParam.name().equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "%s reads the query parameter '%s' but has no @FilterParam for it"
                        .formatted(specInterface.getName(), name)));
  }

  private static Class<?> valueType(Spec spec, Class<?> entity) {
    return comparesText(spec) ? String.class : attributeType(entity, spec.path());
  }

  private static boolean comparesText(Spec spec) {
    return WithoutTypeConversion.class.isAssignableFrom(spec.spec())
        || LikeConcatenated.class.equals(spec.spec());
  }

  private static Class<?> attributeType(Class<?> entity, String path) {
    ResolvableType type = ResolvableType.forClass(entity);
    for (String attribute : path.split("\\.")) {
      Field field = ReflectionUtils.findField(type.toClass(), attribute);
      if (field == null) {
        throw new IllegalStateException(
            "%s has no attribute '%s' of the path '%s'".formatted(type, attribute, path));
      }
      type = ResolvableType.forField(field, type);
      if (Collection.class.isAssignableFrom(type.toClass())) {
        type = type.asCollection().getGeneric(0);
      }
    }
    return type.toClass();
  }

  private static Schema<Object> schemaOf(Class<?> attributeType) {
    Class<?> type = ClassUtils.resolvePrimitiveIfNecessary(attributeType);
    Schema<Object> schema = new Schema<>();
    if (UUID.class.equals(type)) {
      return schema.types(Set.of("string")).format("uuid");
    }
    if (LocalDate.class.equals(type)) {
      return schema.types(Set.of("string")).format("date");
    }
    if (DATE_TIME_TYPES.contains(type)) {
      return schema.types(Set.of("string")).format("date-time");
    }
    if (type.isEnum()) {
      schema.setEnum(
          Arrays.stream(type.getEnumConstants())
              .<Object>map(constant -> ((Enum<?>) constant).name())
              .toList());
      return schema.types(Set.of("string"));
    }
    if (Boolean.class.equals(type)) {
      return schema.types(Set.of("boolean"));
    }
    if (INTEGER_TYPES.contains(type)) {
      return schema.types(Set.of("integer"));
    }
    if (Number.class.isAssignableFrom(type)) {
      return schema.types(Set.of("number"));
    }
    return schema.types(Set.of("string"));
  }

  private static Object typed(String value, Class<?> attributeType) {
    Class<?> type = ClassUtils.resolvePrimitiveIfNecessary(attributeType);
    if (Boolean.class.equals(type)) {
      return Boolean.valueOf(value);
    }
    if (Number.class.isAssignableFrom(type)) {
      return new BigDecimal(value);
    }
    return value;
  }

  private static List<Spec> specsOn(Class<?> specInterface) {
    List<Spec> specs = new ArrayList<>();
    Spec spec = specInterface.getAnnotation(Spec.class);
    if (spec != null) {
      specs.add(spec);
    }
    Or or = specInterface.getAnnotation(Or.class);
    if (or != null) {
      specs.addAll(List.of(or.value()));
    }
    And and = specInterface.getAnnotation(And.class);
    if (and != null) {
      specs.addAll(List.of(and.value()));
    }
    Conjunction conjunction = specInterface.getAnnotation(Conjunction.class);
    if (conjunction != null) {
      Arrays.stream(conjunction.value()).forEach(nested -> specs.addAll(List.of(nested.value())));
      specs.addAll(List.of(conjunction.and()));
    }
    Disjunction disjunction = specInterface.getAnnotation(Disjunction.class);
    if (disjunction != null) {
      Arrays.stream(disjunction.value()).forEach(nested -> specs.addAll(List.of(nested.value())));
      specs.addAll(List.of(disjunction.or()));
    }
    return specs;
  }

  private static Set<Class<?>> specInterfaces(Class<?> specification) {
    Set<Class<?>> interfaces = new LinkedHashSet<>();
    collectSpecInterfaces(specification, interfaces);
    return interfaces;
  }

  private static void collectSpecInterfaces(Class<?> specInterface, Set<Class<?>> interfaces) {
    if (Specification.class.equals(specInterface) || !interfaces.add(specInterface)) {
      return;
    }
    for (Class<?> superInterface : specInterface.getInterfaces()) {
      collectSpecInterfaces(superInterface, interfaces);
    }
  }
}
