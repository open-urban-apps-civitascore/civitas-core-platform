package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.util.InvalidInputException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Registry that maps each {@link ConnectorType} to its corresponding {@link ConnectorHandler}. All
 * registered {@link ConnectorHandler} beans are collected at startup and indexed by their supported
 * type.
 */
@Component
public class ConnectorHandlerRegistry {

  private final Map<ConnectorType, ConnectorHandler> handlers;

  public ConnectorHandlerRegistry(List<ConnectorHandler> handlerList) {
    this.handlers =
        handlerList.stream()
            .collect(Collectors.toMap(ConnectorHandler::getSupportedType, Function.identity()));
  }

  /**
   * Returns the handler for the given connector type, or throws if none is registered.
   *
   * @param type the connector type
   * @return the matching handler
   * @throws InvalidInputException if no handler is registered for the type
   */
  public ConnectorHandler getHandlerOrThrow(ConnectorType type) {
    ConnectorHandler handler = handlers.get(type);
    if (handler == null) {
      throw new InvalidInputException(
          "ConnectorType", String.valueOf(type), "Unsupported connector type: " + type);
    }
    return handler;
  }
}
