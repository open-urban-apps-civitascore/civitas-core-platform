package de.civitascore.portal.util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** One route that a portal-backend controller serves: a path pattern and an HTTP method. */
public record ControllerRoute(String pattern, HttpMethod method, HandlerMethod handler) {

  private static final String CONTROLLER_PACKAGE = "de.civitascore.portal.controller";

  /**
   * The routes of the controllers in {@value #CONTROLLER_PACKAGE}, one per pattern and method. A
   * mapping without a method condition yields one route with a {@code null} method.
   */
  public static List<ControllerRoute> all(RequestMappingHandlerMapping handlerMapping) {
    return handlerMapping.getHandlerMethods().entrySet().stream()
        .filter(entry -> entry.getValue().getBeanType().getPackageName().equals(CONTROLLER_PACKAGE))
        .flatMap(ControllerRoute::routesOf)
        .toList();
  }

  private static Stream<ControllerRoute> routesOf(
      Map.Entry<RequestMappingInfo, HandlerMethod> entry) {
    RequestMappingInfo info = entry.getKey();
    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
    return info.getPathPatternsCondition().getPatternValues().stream()
        .flatMap(
            pattern ->
                methods.isEmpty()
                    ? Stream.of(new ControllerRoute(pattern, null, entry.getValue()))
                    : methods.stream()
                        .map(
                            method ->
                                new ControllerRoute(
                                    pattern, method.asHttpMethod(), entry.getValue())));
  }
}
