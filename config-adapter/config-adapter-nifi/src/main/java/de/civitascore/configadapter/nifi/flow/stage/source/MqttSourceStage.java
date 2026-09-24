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

import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.isEncrypted;
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.putIfPresent;
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.trimmedNonBlank;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.CoreUrn;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.SourceType;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.PayloadForm;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.SourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MQTT push source: ConsumeMQTT subscribing to a single topic filter. Delivers the SensorThings
 * envelope raw — records only exist after a ConvertRecord step, so the stage declares {@link
 * PayloadForm#STA_ENVELOPE}, not {@code RECORDS}.
 */
public final class MqttSourceStage implements SourceStage {

  /** Friendly name the REST client matches for the post-upload sensitive-property push. */
  private static final String MQTT_PROCESSOR = "ConsumeMQTT";

  /** Stable friendly name used for the deterministic controller-service id and processor token. */
  public static final String MQTT_SSL_CONTEXT_SERVICE = "MQTT SSL Context Service";

  private static final String MQTT_SSL_CONTEXT_REFERENCE = "${CS:" + MQTT_SSL_CONTEXT_SERVICE + "}";

  /** Alphanumeric only, to stay within the client ids every broker must accept [MQTT-3.1.3-5]. */
  private static final String CLIENT_ID_PREFIX = "civitascore";

  private static final Set<String> PLAINTEXT_SCHEMES = Set.of("tcp", "ws", "mqtt");
  private static final Set<String> TLS_SCHEMES = Set.of("ssl", "mqtts", "wss");

  /**
   * Paho's {@code TCPNetworkModuleFactory}/{@code SSLNetworkModuleFactory} reject a broker URI with
   * a non-empty path ({@code URI path must be empty}), while the WebSocket factories carry the path
   * as the handshake endpoint. NiFi's {@code customValidate} does not check paths, so a path on a
   * TCP-transport URI deploys clean and only fails when the processor connects.
   */
  private static final Set<String> PATHLESS_SCHEMES = Set.of("tcp", "ssl", "mqtt", "mqtts");

  /**
   * NiFi's ConsumeMQTT (Eclipse Paho) only accepts {@code tcp}/{@code ssl}/{@code ws}/{@code wss}
   * and rejects the {@code mqtt}/{@code mqtts} forms that most brokers and MQTT tooling advertise,
   * so those aliases are mapped to the transport scheme Paho understands. Once Paho registers a
   * native network module for {@code mqtt}/{@code mqtts} this mapping becomes redundant — tracked
   * upstream at https://github.com/eclipse-paho/paho.mqtt.java/issues/464.
   */
  private static final Map<String, String> SCHEME_ALIASES = Map.of("mqtt", "tcp", "mqtts", "ssl");

  /** MQTT major versions mapped to NiFi/Paho/HiveMQ protocol-level values. */
  private static final Map<String, String> MQTT_PROTOCOL_VERSIONS = Map.of("3", "0", "5", "5");

  private static final Pattern BROKER_SCHEME =
      Pattern.compile("^([a-z][a-z0-9+.-]*)://", Pattern.CASE_INSENSITIVE);

  /** The path of a scheme-stripped broker URL: after host[:port], before any query/fragment. */
  private static final Pattern BROKER_PATH = Pattern.compile("^[^/?#]*([^?#]*)");

  /** A plain seconds value, optionally with a seconds unit suffix (e.g. {@code 5}, {@code 5s}). */
  private static final Pattern SECONDS =
      Pattern.compile("(\\d+)\\s*(?:s|sec|secs|second|seconds)?", Pattern.CASE_INSENSITIVE);

  private final CredentialResolver credentials;
  private final MqttTruststoreConfig truststore;

  public MqttSourceStage(CredentialResolver credentials, MqttTruststoreConfig truststore) {
    this.credentials = credentials;
    this.truststore = truststore;
  }

  @Override
  public SourceType type() {
    return SourceType.MQTT;
  }

  @Override
  public PayloadForm output() {
    return PayloadForm.STA_ENVELOPE;
  }

  @Override
  public String cronRejectionMessage() {
    // ConsumeMQTT is push-based (it self-triggers on broker messages), so a cron there would only
    // throttle the drain, not the data. Cron belongs on a pull source (SQL).
    return "cron scheduling is not supported for push-based MQTT sources";
  }

  /**
   * Binds an MQTT datasource to ConsumeMQTT, using the portal's connector field names ({@code
   * urls}/{@code topics} as lists, {@code user}, {@code qos}). Broker URI and Topic Filter are
   * required — without them ConsumeMQTT would fall back to the fragment's demo broker/topic and
   * silently consume from the wrong source, so a missing value fails the deploy.
   */
  @Override
  public void bind(Datasource source, String sourceNodeKey, PlanContext out)
      throws FatalAdapterException {
    Map<String, Object> original = source.getAdditionalProperties();
    Map<String, Object> decrypted = credentials.decrypt(original);
    // NiFi's Broker URI accepts a comma-separated list; Topic Filter is a single filter, so one
    // ConsumeMQTT cannot subscribe to multiple distinct topics — reject rather than mis-build.
    List<String> brokers = trimmedNonBlank(decrypted.get("urls"));
    List<String> topics = trimmedNonBlank(decrypted.get("topics"));
    if (brokers.isEmpty() || topics.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT source requires non-empty 'urls' and 'topics'");
    }
    boolean tlsEnabled = tlsEnabled(decrypted.get("tls"));
    brokers = validateAndNormalizeBrokers(brokers, tlsEnabled);
    if (topics.size() > 1) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "multiple MQTT topics are not supported (one ConsumeMQTT subscribes to a single topic"
              + " filter): "
              + topics);
    }
    out.putSourceProperty("Broker URI", String.join(",", brokers));
    out.putSourceProperty("Topic Filter", topics.get(0));
    if (tlsEnabled) {
      bindTruststore(out);
      out.putSourceProperty("SSL Context Service", MQTT_SSL_CONTEXT_REFERENCE);
    }
    putIfPresent(out::putSourceProperty, "Username", decrypted.get("user"));
    out.putSourceProperty("Client ID", clientId(sourceNodeKey));
    putIfPresent(out::putSourceProperty, "Quality of Service", decrypted.get("qos"));
    bindProtocolVersion(out, decrypted.get("protocol_version"));
    bindSeconds(out, "Connection Timeout", decrypted.get("connect_timeout"));
    bindSeconds(out, "Keep Alive", decrypted.get("keepalive"));
    // A password, if present, must be encrypted: a plaintext secret must never be written into
    // the (logged, non-secret) flow snapshot, and silently dropping it would deploy a flow that
    // connects to the broker unauthenticated.
    Object password = original.get("password");
    if (password != null && !String.valueOf(password).isBlank() && !isEncrypted(password)) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT source password must be encrypted (plaintext secrets are not accepted)");
    }
    if (isEncrypted(password)) {
      out.putSensitive(MQTT_PROCESSOR, "Password", String.valueOf(decrypted.get("password")));
    }
  }

  /**
   * Checks the trust anchor and collects its literal password. A password parameter contributes
   * only its <em>name</em> to the snapshot, applied with the other truststore properties in {@link
   * #registerControllerServices} — the value lives in NiFi, supplied by the deployment, so no
   * truststore secret passes through the adapter. A literal password (the JDK store's {@code
   * changeit}) instead goes the post-upload sensitive route, never into the snapshot. A missing
   * anchor fails the deploy instead of falling back to plaintext or to NiFi's node truststore.
   */
  private void bindTruststore(PlanContext out) throws FatalAdapterException {
    if (truststore.path().isEmpty() || truststore.type().isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT TLS needs a truststore: set nifi.mqtt.truststore.path and"
              + " nifi.mqtt.truststore.type");
    }
    if (truststore.hasPasswordParameter() && truststore.parameterContext().isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT truststore password parameter '"
              + truststore.passwordParameter()
              + "' needs nifi.mqtt.truststore.parameter-context");
    }
    truststore
        .sensitiveProperties()
        .forEach((key, value) -> out.putSensitive(MQTT_SSL_CONTEXT_SERVICE, key, value));
  }

  /** Hashed, because the key has neither the length nor the characters a client id allows. */
  private static String clientId(String sourceNodeKey) {
    UUID name = UUID.nameUUIDFromBytes(sourceNodeKey.getBytes(StandardCharsets.UTF_8));
    return CLIENT_ID_PREFIX + CoreUrn.disambiguatorFor(name);
  }

  @Override
  public void registerControllerServices(BuildContext ctx) throws FatalAdapterException {
    if (tlsRequested(ctx)) {
      ctx.addControllerService(Fragment.MQTT_SSL_CONTEXT_SERVICE, MQTT_SSL_CONTEXT_SERVICE);
      for (Map.Entry<String, String> property : truststore.sslContextProperties().entrySet()) {
        ctx.setControllerServiceExpression(
            MQTT_SSL_CONTEXT_SERVICE, property.getKey(), property.getValue());
      }
    }
  }

  /**
   * Binds the flow to the Parameter Context holding the truststore password; a store that opens
   * without one needs no context at all. The declaration carries no value — the deployment
   * populates the sensitive parameter in NiFi.
   */
  @Override
  public Optional<ParameterContextSpec> parameterContext(BuildContext ctx) {
    if (!tlsRequested(ctx) || !truststore.hasPasswordParameter()) {
      return Optional.empty();
    }
    return Optional.of(
        new ParameterContextSpec(
            truststore.parameterContext(),
            List.of(
                new ParameterSpec(
                    truststore.passwordParameter(), "Password of the MQTT truststore", true))));
  }

  private static boolean tlsRequested(BuildContext ctx) {
    return MQTT_SSL_CONTEXT_REFERENCE.equals(
        ctx.spec().sourceProperties().get("SSL Context Service"));
  }

  @Override
  public StageResult build(BuildContext ctx) throws FatalAdapterException {
    Processor source = ctx.loadProcessor(Fragment.CONSUME_MQTT, "Message");
    BuildContext.applySchedule(source, ctx.spec().sourceCron());
    for (Map.Entry<String, String> property : ctx.spec().sourceProperties().entrySet()) {
      if ("SSL Context Service".equals(property.getKey())
          && MQTT_SSL_CONTEXT_REFERENCE.equals(property.getValue())) {
        ctx.setControllerServiceProp(source, property.getKey(), MQTT_SSL_CONTEXT_SERVICE);
      } else {
        BuildContext.setProp(source, property.getKey(), property.getValue());
      }
    }
    return new StageResult(List.of(source), List.of());
  }

  private static boolean tlsEnabled(Object tls) {
    return tls instanceof Map<?, ?> map
        && "true".equalsIgnoreCase(String.valueOf(map.get("enabled")));
  }

  /**
   * Validates every broker against the TLS switch, normalizes the {@code mqtt}/{@code mqtts}
   * aliases to the transport schemes NiFi accepts, and rejects a list whose entries do not all end
   * up on one transport — {@code AbstractMQTTProcessor.customValidate} compares every URI's scheme
   * to the first and fails the processor with {@code all URIs should use the same scheme}.
   */
  private static List<String> validateAndNormalizeBrokers(List<String> brokers, boolean tlsEnabled)
      throws FatalAdapterException {
    List<String> normalized = new ArrayList<>(brokers.size());
    Set<String> expected = tlsEnabled ? TLS_SCHEMES : PLAINTEXT_SCHEMES;
    for (String broker : brokers) {
      Matcher matcher = BROKER_SCHEME.matcher(broker);
      String scheme = matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "";
      if (!PLAINTEXT_SCHEMES.contains(scheme) && !TLS_SCHEMES.contains(scheme)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "unsupported MQTT broker scheme in URL: " + broker);
      }
      if (!expected.contains(scheme)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "MQTT broker scheme '" + scheme + "' is inconsistent with tls.enabled=" + tlsEnabled);
      }
      String remainder = broker.substring(matcher.end());
      if (PATHLESS_SCHEMES.contains(scheme) && hasPath(remainder)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "MQTT broker scheme '"
                + scheme
                + "' does not accept a path, use a 'ws'/'wss' URL for a path-addressed broker: "
                + broker);
      }
      normalized.add(SCHEME_ALIASES.getOrDefault(scheme, scheme) + "://" + remainder);
    }
    Set<String> transports =
        normalized.stream()
            .map(BROKER_SCHEME::matcher)
            .filter(Matcher::find)
            .map(matcher -> matcher.group(1))
            .collect(Collectors.toUnmodifiableSet());
    if (transports.size() > 1) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "all MQTT brokers must use the same transport, got " + transports + ": " + normalized);
    }
    return List.copyOf(normalized);
  }

  /** A bare {@code /} is what Paho itself produces for an empty path, so it does not count. */
  private static boolean hasPath(String authorityAndPath) {
    Matcher matcher = BROKER_PATH.matcher(authorityAndPath);
    if (!matcher.find()) {
      return false;
    }
    String path = matcher.group(1);
    return !path.isEmpty() && !"/".equals(path);
  }

  /**
   * Binds a portal duration (the connector sends e.g. {@code "5s"}/{@code "30s"}) to a NiFi
   * property as the plain integer seconds it expects. An absent/blank value is skipped; a non-blank
   * value that is not a simple seconds duration is rejected rather than silently dropped (which
   * would leave NiFi's default).
   */
  private void bindSeconds(PlanContext out, String nifiKey, Object value)
      throws FatalAdapterException {
    if (value == null) {
      return;
    }
    String text = String.valueOf(value).trim();
    if (text.isEmpty()) {
      return;
    }
    Matcher matcher = SECONDS.matcher(text);
    if (!matcher.matches()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT '" + nifiKey + "' is not a valid seconds duration: " + text);
    }
    out.putSourceProperty(nifiKey, matcher.group(1));
  }

  private void bindProtocolVersion(PlanContext out, Object value) throws FatalAdapterException {
    // Missing values belong to pre-version-field datasources and retain the old fragment behaviour:
    // Paho's v3 AUTO mode tries MQTT 3.1.1 first, then falls back to MQTT 3.1.0.
    String configured = value == null ? "3" : String.valueOf(value).trim();
    String nifiValue = MQTT_PROTOCOL_VERSIONS.get(configured);
    if (nifiValue == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "MQTT protocol version must be 3 or 5");
    }
    out.putSourceProperty("MQTT Specification Version", nifiValue);
  }
}
