/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.owasp.encoder.Encode;
import org.slf4j.LoggerFactory;

/**
 * Base class for API model objects that support additional (unknown) JSON properties. Provides a
 * consistent {@link JsonAnyGetter @JsonAnyGetter}/{@link JsonAnySetter @JsonAnySetter}
 * implementation with OWASP-encoded debug logging.
 *
 * <p>Subclasses inherit:
 *
 * <ul>
 *   <li>{@link #getAdditionalProperties()} — returns an unmodifiable view for Jackson serialization
 *   <li>{@link #handleUnknownProperty(String, Object)} — stores unknown JSON keys with debug
 *       logging
 *   <li>{@link #additionalProperties()} — protected accessor for use in {@code toApiMap()}, {@code
 *       equals()}, and {@code hashCode()}
 * </ul>
 */
public abstract class AbstractApiModel {

  private final Map<String, Object> additionalProperties = new LinkedHashMap<>();
  private final Map<String, Object> additionalPropertiesView =
      Collections.unmodifiableMap(additionalProperties);

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalPropertiesView;
  }

  @JsonAnySetter
  public void handleUnknownProperty(String key, Object value) {
    LoggerFactory.getLogger(getClass())
        .debug("Unknown property '{}' in {}", Encode.forJava(key), getClass().getSimpleName());
    additionalProperties.put(key, value);
  }

  /**
   * Converts this model to a plain map suitable for external API calls. Subclasses map their typed
   * fields and merge {@link #additionalProperties()} for any unknown JSON properties.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  public abstract Map<String, Object> toApiMap();

  /**
   * Returns an unmodifiable view of the additional (unknown) JSON properties captured by {@link
   * #handleUnknownProperty(String, Object)}. The view reflects mutations from {@code
   * handleUnknownProperty} but cannot be modified directly.
   *
   * <p>Intended for subclass use in {@code toApiMap()}, {@code equals()}, and {@code hashCode()}.
   */
  protected Map<String, Object> additionalProperties() {
    return additionalPropertiesView;
  }
}
