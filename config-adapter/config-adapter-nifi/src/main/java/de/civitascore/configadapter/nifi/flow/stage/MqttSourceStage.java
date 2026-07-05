/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.isEncrypted;
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.putIfPresent;
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.trimmedNonBlank;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.SourceType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MQTT push source: ConsumeMQTT subscribing to a single topic filter. Delivers the SensorThings
 * envelope raw — records only exist after a ConvertRecord step, so the stage declares {@link
 * PayloadForm#STA_ENVELOPE}, not {@code RECORDS}.
 */
public final class MqttSourceStage implements SourceStage {

  /** Friendly name the REST client matches for the post-upload sensitive-property push. */
  private static final String MQTT_PROCESSOR = "ConsumeMQTT";

  /** A plain seconds value, optionally with a seconds unit suffix (e.g. {@code 5}, {@code 5s}). */
  private static final Pattern SECONDS =
      Pattern.compile("(\\d+)\\s*(?:s|sec|secs|second|seconds)?", Pattern.CASE_INSENSITIVE);

  private final CredentialResolver credentials;

  public MqttSourceStage(CredentialResolver credentials) {
    this.credentials = credentials;
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
   * urls}/{@code topics} as lists, {@code user}, {@code client_id}, {@code qos}). Broker URI and
   * Topic Filter are required — without them ConsumeMQTT would fall back to the fragment's demo
   * broker/topic and silently consume from the wrong source, so a missing value fails the deploy.
   */
  @Override
  public void bind(Datasource source, PlanContext out) throws FatalAdapterException {
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
    rejectTlsSource(decrypted.get("tls"), brokers);
    if (topics.size() > 1) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "multiple MQTT topics are not supported (one ConsumeMQTT subscribes to a single topic"
              + " filter): "
              + topics);
    }
    out.putSourceProperty("Broker URI", String.join(",", brokers));
    out.putSourceProperty("Topic Filter", topics.get(0));
    putIfPresent(out.sourceProperties(), "Username", decrypted.get("user"));
    putIfPresent(out.sourceProperties(), "Client ID", decrypted.get("client_id"));
    putIfPresent(out.sourceProperties(), "Quality of Service", decrypted.get("qos"));
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

  @Override
  public StageResult build(BuildContext ctx) throws FatalAdapterException {
    Processor source = ctx.loadProcessor(Fragment.CONSUME_MQTT, "Message");
    BuildContext.applySchedule(source, ctx.spec().sourceCron());
    ctx.spec().sourceProperties().forEach((key, value) -> BuildContext.setProp(source, key, value));
    return new StageResult(List.of(source), List.of());
  }

  /**
   * Rejects a TLS MQTT source: NiFi requires an SSL Context Service for a TLS broker, which this
   * adapter does not provision, so deploying would silently fall back to a plaintext connection.
   * Both an explicit {@code tls.enabled=true} and a TLS broker scheme ({@code ssl://}/{@code
   * mqtts://}/{@code wss://}) are rejected.
   */
  private void rejectTlsSource(Object tls, List<String> brokers) throws FatalAdapterException {
    boolean tlsEnabled =
        tls instanceof Map<?, ?> map && "true".equalsIgnoreCase(String.valueOf(map.get("enabled")));
    boolean tlsScheme =
        brokers.stream()
            .map(b -> b.toLowerCase(Locale.ROOT))
            .anyMatch(
                b -> b.startsWith("ssl://") || b.startsWith("mqtts://") || b.startsWith("wss://"));
    if (tlsEnabled || tlsScheme) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT TLS is not supported yet (needs a NiFi SSL Context Service)");
    }
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
}
