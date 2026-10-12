package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.ConnectorJson;

/**
 * A violated rule of a connector.
 *
 * @param path the key path of the value in the request body, e.g. {@code connector.qos}
 * @param message what is wrong, without the value, because a value can carry a credential
 */
public record ConnectorViolation(String path, String message) {

  /**
   * A violation at a key path below the {@code connector} member.
   *
   * @param keyPath the key path without the member, e.g. {@code apiCalls[0].auth.token}; empty for
   *     a violation of the whole connector
   */
  public static ConnectorViolation at(String keyPath, String message) {
    return new ConnectorViolation(
        keyPath.isEmpty() ? ConnectorJson.ROOT : ConnectorJson.ROOT + "." + keyPath, message);
  }
}
