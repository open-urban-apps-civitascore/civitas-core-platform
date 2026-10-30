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

import de.civitascore.configadapter.connector.ConnectorValueException;
import de.civitascore.configadapter.connector.MqttConnectorValues;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
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
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

  /** Keeps prefix plus id within the 23 characters MQTT 3.1 allows a client id. */
  private static final int SHORT_ID_LENGTH = 10;

  /** MQTT major versions mapped to NiFi/Paho/HiveMQ protocol-level values. */
  private static final Map<String, String> MQTT_PROTOCOL_VERSIONS = Map.of("3", "0", "5", "5");

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
  public void bind(
      Datasource source,
      Map<String, Map<String, Object>> artifacts,
      GraphNode node,
      String sourceNodeKey,
      PlanContext out)
      throws FatalAdapterException {
    Map<String, Object> original = source.getAdditionalProperties();
    Map<String, Object> decrypted = credentials.decrypt(original);
    MqttConnectorValues values = readValues(decrypted);
    out.putSourceProperty("Broker URI", String.join(",", values.brokers()));
    out.putSourceProperty("Topic Filter", values.topic());
    if (values.tls()) {
      bindTruststore(out);
      out.putSourceProperty("SSL Context Service", MQTT_SSL_CONTEXT_REFERENCE);
    }
    putIfPresent(out::putSourceProperty, "Username", decrypted.get("user"));
    out.putSourceProperty("Client ID", clientId(sourceNodeKey));
    putIfPresent(out::putSourceProperty, "Quality of Service", decrypted.get("qos"));
    out.putSourceProperty(
        "MQTT Specification Version", MQTT_PROTOCOL_VERSIONS.get(values.protocolVersion()));
    putIfPresent(out::putSourceProperty, "Connection Timeout", values.connectTimeoutSeconds());
    putIfPresent(out::putSourceProperty, "Keep Alive", values.keepAliveSeconds());
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
    return CLIENT_ID_PREFIX + shortId(name);
  }

  /**
   * Must reproduce the client ids deployed flows already carry; another derivation renames every
   * flow's broker client on its next deploy.
   */
  private static String shortId(UUID id) {
    ByteBuffer bytes =
        ByteBuffer.allocate(16)
            .putLong(id.getMostSignificantBits())
            .putLong(id.getLeastSignificantBits());
    String base36 = new BigInteger(1, bytes.array()).toString(36);
    String tail =
        base36.length() > SHORT_ID_LENGTH
            ? base36.substring(base36.length() - SHORT_ID_LENGTH)
            : base36;
    return "0".repeat(SHORT_ID_LENGTH - tail.length()) + tail;
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

  private static MqttConnectorValues readValues(Map<String, Object> decrypted)
      throws FatalAdapterException {
    try {
      return MqttConnectorValues.read(decrypted);
    } catch (ConnectorValueException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
  }
}
