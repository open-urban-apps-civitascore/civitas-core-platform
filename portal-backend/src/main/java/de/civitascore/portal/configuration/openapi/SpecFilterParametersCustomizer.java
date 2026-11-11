package de.civitascore.portal.configuration.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Documents the query parameters of a list operation from the specification interface its handler
 * takes, see {@link SpecFilterParameters}.
 */
@Component
public class SpecFilterParametersCustomizer implements OperationCustomizer {

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    for (MethodParameter methodParameter : handlerMethod.getMethodParameters()) {
      Class<?> type =
          methodParameter.withContainingClass(handlerMethod.getBeanType()).getParameterType();
      if (Specification.class.isAssignableFrom(type)) {
        SpecFilterParameters.of(type)
            .forEach(parameter -> add(operation, parameter, handlerMethod));
      }
    }
    return operation;
  }

  private static void add(Operation operation, Parameter parameter, HandlerMethod handlerMethod) {
    if (operation.getParameters() != null
        && operation.getParameters().stream()
            .anyMatch(
                existing ->
                    "query".equals(existing.getIn())
                        && parameter.getName().equals(existing.getName()))) {
      throw new IllegalStateException(
          "%s documents the filter parameter '%s' by hand; its @Spec interface documents it"
              .formatted(handlerMethod.getShortLogMessage(), parameter.getName()));
    }
    operation.addParametersItem(parameter);
  }
}
