/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.source;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trust anchor the MQTT SSL Context Service validates broker certificates against. It is
 * environment-level, not per-datasource, because NiFi resolves the store from a filesystem path
 * only the deployment can provision.
 *
 * <p>{@code passwordParameter} names a NiFi sensitive parameter, never a password: the value is
 * written into NiFi by the deployment and never travels through the adapter or a flow snapshot.
 */
public record MqttTruststoreConfig(
    String path, String type, String passwordParameter, String parameterContext) {

  /**
   * NiFi's own node truststore. The literal survives into the property because NiFi evaluates
   * Truststore Filename against its environment, where the apache/nifi image sets this variable.
   */
  public static final String NODE_TRUSTSTORE_PATH = "${TRUSTSTORE_PATH}";

  public static final String DEFAULT_TYPE = "PKCS12";

  /** Parameter Context the NiFi deployment provisions for the node truststore password. */
  public static final String DEFAULT_PARAMETER_CONTEXT = "NiFi Node Truststore";

  /** Sensitive parameter supplied by the deployment, never serialized with a value. */
  public static final String DEFAULT_PASSWORD_PARAMETER = "TRUSTSTORE_PASSWORD";

  /**
   * Declares a truststore that opens without a password, so no Parameter Context is needed. NiFi
   * validates a truststore from filename and type alone — its Truststore Password property is
   * {@code required(false)}. An empty value cannot express this: the configuration layer treats an
   * empty environment variable as unset and falls back to the default.
   */
  public static final String NO_PASSWORD = "none";

  public MqttTruststoreConfig {
    path = normalize(path);
    type = normalize(type);
    passwordParameter = normalizePasswordParameter(passwordParameter);
    parameterContext = normalize(parameterContext);
  }

  /** The node truststore behind the deployment-owned parameter context. */
  public static MqttTruststoreConfig nodeTruststore() {
    return new MqttTruststoreConfig(
        NODE_TRUSTSTORE_PATH, DEFAULT_TYPE, DEFAULT_PASSWORD_PARAMETER, DEFAULT_PARAMETER_CONTEXT);
  }

  /** Whether a truststore password is supplied at all — NiFi treats the property as optional. */
  public boolean hasPasswordParameter() {
    return !passwordParameter.isEmpty();
  }

  /**
   * The SSL Context Service properties this anchor contributes. They are the only source, because
   * the fragment declares them unset: a service that receives none of them fails NiFi's validation
   * with "Either the keystore and/or truststore must be populated".
   */
  public Map<String, String> sslContextProperties() {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("Truststore Filename", path);
    properties.put("Truststore Type", type);
    if (hasPasswordParameter()) {
      properties.put("Truststore Password", "#{" + passwordParameter + "}");
    }
    return Collections.unmodifiableMap(properties);
  }

  private static String normalizePasswordParameter(String value) {
    String normalized = normalize(value);
    return NO_PASSWORD.equalsIgnoreCase(normalized) ? "" : normalized;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip();
  }
}
