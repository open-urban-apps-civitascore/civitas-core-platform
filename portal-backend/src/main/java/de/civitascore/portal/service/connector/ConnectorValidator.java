package de.civitascore.portal.service.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.IdentifiedElement;
import de.civitascore.portal.model.connector.OnRelease;
import de.civitascore.portal.model.embedded.ConnectorType;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.spi.nodenameprovider.JavaBeanProperty;
import org.hibernate.validator.spi.nodenameprovider.Property;
import org.hibernate.validator.spi.nodenameprovider.PropertyNodeNameProvider;
import org.springframework.stereotype.Service;

/**
 * Validates a connector against the Bean Validation annotations on its records, the {@link
 * ConnectorRules} of its type and one rule for all types: every list element that implements {@link
 * IdentifiedElement} has an identity, unique within its list.
 */
@Service
public class ConnectorValidator {

  /** The stage whose rules a connector must meet. */
  public enum Stage {
    /** Types, ranges and patterns. A connector that violates them cannot be saved. */
    DRAFT(Default.class),
    /**
     * The rules of {@link #DRAFT} and completeness. A connector that violates them blocks the
     * release.
     */
    RELEASE(Default.class, OnRelease.class);

    private final Class<?>[] groups;

    Stage(Class<?>... groups) {
      this.groups = groups;
    }
  }

  private final ValidatorFactory validatorFactory =
      Validation.byProvider(HibernateValidator.class)
          .configure()
          .propertyNodeNameProvider(new JsonKeyProvider())
          .buildValidatorFactory();
  private final Validator validator = validatorFactory.getValidator();
  private final Map<ConnectorType, ConnectorRules> rulesByType;

  /**
   * @throws IllegalStateException if two rules beans have the same connector type
   */
  public ConnectorValidator(List<ConnectorRules> rules) {
    this.rulesByType =
        rules.stream()
            .collect(Collectors.toMap(ConnectorRules::connectorType, Function.identity()));
  }

  /**
   * @param connector the connector, or null for none
   * @return the violations, sorted by path and message; empty for a null connector
   */
  public List<ConnectorViolation> validate(ConnectorConfiguration connector, Stage stage) {
    if (connector == null) {
      return List.of();
    }
    List<ConnectorViolation> violations = new ArrayList<>();
    validator.validate(connector, stage.groups).stream()
        .map(v -> ConnectorViolation.at(v.getPropertyPath().toString(), v.getMessage()))
        .forEach(violations::add);
    addIdentityViolations(connector, "", violations);
    ConnectorRules rules = rulesByType.get(connector.connectorType());
    if (rules != null) {
      violations.addAll(rules.validate(connector, stage));
    }
    violations.sort(
        Comparator.comparing(ConnectorViolation::path).thenComparing(ConnectorViolation::message));
    return violations;
  }

  @PreDestroy
  void close() {
    validatorFactory.close();
  }

  private static void addIdentityViolations(
      Object value, String keyPath, List<ConnectorViolation> violations) {
    if (value instanceof Record rec) {
      for (RecordComponent component : rec.getClass().getRecordComponents()) {
        String key = jsonKey(rec.getClass(), component.getName());
        addIdentityViolations(
            componentValue(rec, component),
            keyPath.isEmpty() ? key : keyPath + "." + key,
            violations);
      }
    } else if (value instanceof List<?> list) {
      Set<Object> identities = new HashSet<>();
      for (int i = 0; i < list.size(); i++) {
        String elementPath = keyPath + "[" + i + "]";
        if (list.get(i) instanceof IdentifiedElement element) {
          if (element.identity() == null) {
            violations.add(ConnectorViolation.at(elementPath, "must have an identity"));
          } else if (!identities.add(element.identity())) {
            violations.add(
                ConnectorViolation.at(elementPath, "must have an identity unique within its list"));
          }
        }
        addIdentityViolations(list.get(i), elementPath, violations);
      }
    }
  }

  private static Object componentValue(Record rec, RecordComponent component) {
    try {
      return component.getAccessor().invoke(rec);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot read " + component, e);
    }
  }

  static String jsonKey(Class<?> type, String member) {
    try {
      JsonProperty property = type.getDeclaredField(member).getAnnotation(JsonProperty.class);
      return property == null || property.value().isEmpty() ? member : property.value();
    } catch (NoSuchFieldException e) {
      return member;
    }
  }

  /** Names the nodes of a Bean Validation property path by their keys in the request body. */
  private static final class JsonKeyProvider implements PropertyNodeNameProvider {
    @Override
    public String getName(Property property) {
      return property instanceof JavaBeanProperty bean
          ? jsonKey(bean.getDeclaringClass(), bean.getMemberName())
          : property.getName();
    }
  }
}
