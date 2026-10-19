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
 * <p>A store opens in one of three ways, and they are mutually exclusive. {@code passwordParameter}
 * names a NiFi sensitive parameter whose value the deployment writes into NiFi, so no secret
 * travels through the adapter — the right choice for a store with a real password. {@code password}
 * is a literal pushed as a sensitive property after upload; it exists for well-known store
 * passwords such as the JDK's {@code changeit}, which cannot be expressed as a parameter reference.
 * Neither one set means the store opens without a password at all. A parameter always wins over a
 * literal, so a deployment that names one never also ships the default literal.
 */
public record MqttTruststoreConfig(
    String path, String type, String password, String passwordParameter, String parameterContext) {

  /**
   * The JVM's own trust store, holding every publicly trusted root CA the JDK ships with. The
   * literal survives into the property because {@code Truststore Filename} is evaluated with {@code
   * ExpressionLanguageScope.ENVIRONMENT}, and the NiFi image sets this variable.
   */
  public static final String JDK_TRUSTSTORE_PATH = "${JAVA_HOME}/lib/security/cacerts";

  /**
   * The JDK trust store's conventional password. A published constant rather than a secret, but it
   * cannot simply be omitted — see the truststore section of {@code DEPLOYMENT.md} for why a blank
   * password fails NiFi's validation.
   */
  public static final String JDK_TRUSTSTORE_PASSWORD = "changeit";

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

  /** The SSL Context Service property a password — literal or parameter reference — lands in. */
  private static final String TRUSTSTORE_PASSWORD_PROPERTY = "Truststore Password";

  public MqttTruststoreConfig {
    path = normalize(path);
    type = normalize(type);
    passwordParameter = normalizePasswordParameter(passwordParameter);
    parameterContext = normalize(parameterContext);
    password = passwordParameter.isEmpty() ? normalize(password) : "";
  }

  /**
   * The JDK trust store, opened with its conventional password. Every publicly trusted CA is in it,
   * so a broker with a Let's Encrypt certificate needs no configuration; a private CA does, and
   * belongs in a dedicated store the deployment provisions.
   */
  public static MqttTruststoreConfig jdkTruststore() {
    return new MqttTruststoreConfig(
        JDK_TRUSTSTORE_PATH, DEFAULT_TYPE, JDK_TRUSTSTORE_PASSWORD, "", "");
  }

  /**
   * The node truststore behind the deployment-owned parameter context. No longer a shipped default
   * — {@link #jdkTruststore()} is — and today only test fixtures build it.
   */
  public static MqttTruststoreConfig nodeTruststore() {
    return new MqttTruststoreConfig(
        NODE_TRUSTSTORE_PATH,
        DEFAULT_TYPE,
        "",
        DEFAULT_PASSWORD_PARAMETER,
        DEFAULT_PARAMETER_CONTEXT);
  }

  /** Whether the password comes from a NiFi parameter the deployment populates. */
  public boolean hasPasswordParameter() {
    return !passwordParameter.isEmpty();
  }

  /** Whether a literal password is configured, to be pushed after upload. */
  public boolean hasPassword() {
    return !password.isEmpty();
  }

  /**
   * The SSL Context Service properties this anchor contributes to the snapshot. They are the only
   * source, because the fragment declares them unset: a service that receives none of them fails
   * NiFi's validation with "Either the keystore and/or truststore must be populated". A literal
   * password is deliberately absent here — see {@link #sensitiveProperties()}.
   */
  public Map<String, String> sslContextProperties() {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("Truststore Filename", path);
    properties.put("Truststore Type", type);
    if (hasPasswordParameter()) {
      properties.put(TRUSTSTORE_PASSWORD_PROPERTY, "#{" + passwordParameter + "}");
    }
    return Collections.unmodifiableMap(properties);
  }

  /**
   * The properties pushed onto the SSL Context Service over REST after the snapshot is uploaded. A
   * literal password takes this route rather than the snapshot because NiFi does not carry values
   * for sensitive properties through a flow definition. The controller service is validated when it
   * is enabled, which happens after the push.
   */
  public Map<String, String> sensitiveProperties() {
    return hasPassword() ? Map.of(TRUSTSTORE_PASSWORD_PROPERTY, password) : Collections.emptyMap();
  }

  /** Masks the literal password: this record reaches logs and error messages. */
  @Override
  public String toString() {
    return "MqttTruststoreConfig[path=%s, type=%s, password=%s, passwordParameter=%s, parameterContext=%s]"
        .formatted(path, type, hasPassword() ? "***" : "", passwordParameter, parameterContext);
  }

  private static String normalizePasswordParameter(String value) {
    String normalized = normalize(value);
    return NO_PASSWORD.equalsIgnoreCase(normalized) ? "" : normalized;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip();
  }
}
