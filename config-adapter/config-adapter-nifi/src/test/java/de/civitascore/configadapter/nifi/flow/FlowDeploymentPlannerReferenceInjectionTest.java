/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.MAPPINGS;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.MAP_BASIC;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.MAP_FROST;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithFrostMapping;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithMapping;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.map;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.mqttSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.planner;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.postgisSink;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.postgisSinkWithPk;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.sqlSourceBasic;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.stretchedKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.SinkSpec;
import de.civitascore.configadapter.nifi.mapping.UnsafePropertyValueException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every tenant-controlled field that reaches a NiFi property, fed an Expression Language and a
 * parameter reference. NiFi would expand either against its own environment or parameter context
 * and hand the result to a system the tenant controls.
 */
class FlowDeploymentPlannerReferenceInjectionTest {

  private static final List<String> REFERENCES = List.of("${HOSTNAME}", "#{MQTT_TRUSTSTORE}");

  private record Case(
      Map<String, Object> graph, Datasource source, SinkSpec sink, Map<String, Object> mappings) {}

  @FunctionalInterface
  private interface CaseFactory {
    Case create(String reference, byte[] key) throws Exception;
  }

  private static final Map<String, CaseFactory> FIELDS = new LinkedHashMap<>();

  static {
    FIELDS.put(
        "MQTT urls",
        (ref, key) ->
            postgis(
                mqtt(
                    source ->
                        source.handleUnknownProperty("urls", List.of("tcp://" + ref + ":1883")))));
    FIELDS.put(
        "MQTT topics",
        (ref, key) ->
            postgis(mqtt(source -> source.handleUnknownProperty("topics", List.of("s/" + ref)))));
    FIELDS.put(
        "MQTT user",
        (ref, key) -> postgis(mqtt(source -> source.handleUnknownProperty("user", ref))));
    FIELDS.put(
        "MQTT password",
        (ref, key) ->
            postgis(mqtt(source -> source.handleUnknownProperty("password", encrypted(ref, key)))));
    FIELDS.put(
        "SQL dsn",
        (ref, key) ->
            postgisWithPk(
                sql(
                    source ->
                        source.handleUnknownProperty(
                            "dsn", "postgres://reader@srcdb:5432/in?ApplicationName=" + ref))));
    FIELDS.put(
        "SQL user",
        (ref, key) -> postgisWithPk(sql(source -> source.handleUnknownProperty("user", ref))));
    FIELDS.put(
        "SQL password",
        (ref, key) ->
            postgisWithPk(
                sql(source -> source.handleUnknownProperty("password", encrypted(ref, key)))));
    FIELDS.put(
        "SQL table",
        (ref, key) -> postgisWithPk(sql(source -> source.handleUnknownProperty("table", ref))));
    FIELDS.put(
        "SQL columns",
        (ref, key) ->
            postgisWithPk(
                sql(source -> source.handleUnknownProperty("columns", List.of("id", ref)))));
    FIELDS.put(
        "SQL where",
        (ref, key) ->
            postgisWithPk(
                sql(source -> source.handleUnknownProperty("where", "name = '" + ref + "'"))));
    FIELDS.put(
        "PostGIS table name",
        (ref, key) ->
            new Case(graphWithMapping(), mqttSource(null), new PostgisSinkSpec(ref), MAPPINGS));
    FIELDS.put(
        "PostGIS schema name",
        (ref, key) ->
            new Case(
                graphWithMapping(),
                mqttSource(null),
                new PostgisSinkSpec("t", ref, List.of()),
                MAPPINGS));
    FIELDS.put(
        "PostGIS primary key",
        (ref, key) ->
            new Case(
                graphWithMapping(),
                mqttSource(null),
                new PostgisSinkSpec("t", List.of(ref)),
                MAPPINGS));
    FIELDS.put(
        "mapping const",
        (ref, key) ->
            new Case(
                graphWithMapping(),
                mqttSource(null),
                postgisSink(),
                mappingsWith(
                    MAP_BASIC, "{\"$.unit\":{\"op\":\"const\",\"value\":\"" + ref + "\"}}")));
    FIELDS.put(
        "mapping concat separator",
        (ref, key) ->
            new Case(
                graphWithMapping(),
                mqttSource(null),
                postgisSink(),
                mappingsWith(
                    MAP_BASIC,
                    "{\"$.label\":{\"op\":\"concat\",\"inputs\":[\"$.a\",\"$.b\"],\"separator\":\""
                        + ref
                        + "\"}}")));
    FIELDS.put(
        "FROST mapping const",
        (ref, key) ->
            new Case(
                graphWithFrostMapping(),
                mqttSource(null),
                new FrostSinkSpec("7", NifiTestFixtures.STA_KEYS),
                mappingsWith(
                    MAP_FROST,
                    "{\"$.name\":\"$.station\",\"$.description\":{\"op\":\"const\",\"value\":\""
                        + ref
                        + "\"},\"$.properties.reference\":\"$.ref\","
                        + "\"$.Datastreams[].properties.reference\":\"$.ref\","
                        + "\"$.Datastreams[].Observations[].result\":{\"op\":\"toFloat\",\"input\":\"$.temp\"},"
                        + "\"$.Datastreams[].Observations[].phenomenonTime\":\"$.ts\"}")));
  }

  static Stream<Arguments> tenantFieldsWithReferences() {
    return FIELDS.keySet().stream()
        .flatMap(field -> REFERENCES.stream().map(reference -> Arguments.of(field, reference)));
  }

  @ParameterizedTest(name = "{0} = {1}")
  @MethodSource("tenantFieldsWithReferences")
  void plan_tenantFieldWithReference_isRejectedWithoutEchoingTheValue(
      String field, String reference) throws Exception {
    byte[] key = stretchedKey();
    Case input = FIELDS.get(field).create(reference, key);

    try (CredentialResolver resolver = new CredentialResolver(key)) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-ref",
                              input.graph(),
                              input.source(),
                              input.sink(),
                              input.mappings())));

      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
      assertInstanceOf(UnsafePropertyValueException.class, ex.getCause());
      assertFalse(String.valueOf(ex.getMessage()).contains(reference), ex.getMessage());
    }
  }

  /**
   * The {@code $}→{@code $$} escape corrupted every lone dollar sign, since NiFi keeps it doubled.
   */
  @Test
  void plan_literalDollarSigns_reachTheFlowUnchanged() throws Exception {
    byte[] key = stretchedKey();
    Datasource source = mqttSource(encrypted("pa$$word$", key));
    Map<String, Object> mappings =
        mappingsWith(MAP_BASIC, "{\"$.price\":{\"op\":\"const\",\"value\":\"US$5\"}}");

    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-dollar",
                      graphWithMapping(),
                      source,
                      new PostgisSinkSpec("price$eur", List.of("id$")),
                      mappings));

      String snapshot = plan.snapshotJson();
      assertTrue(snapshot.contains("\"price$eur\""), "table name");
      assertTrue(snapshot.contains("\"id$\""), "update keys");
      assertTrue(snapshot.contains("\"US$5\""), "mapping const");
      assertEquals(
          "pa$$word$", plan.sensitivePropsByComponent().get("ConsumeMQTT").get("Password"));
    }
  }

  private interface SourceCustomizer {
    void apply(Datasource source) throws Exception;
  }

  private static Datasource mqtt(SourceCustomizer customizer) throws Exception {
    Datasource source = mqttSource(null);
    customizer.apply(source);
    return source;
  }

  private static Datasource sql(SourceCustomizer customizer) throws Exception {
    Datasource source = sqlSourceBasic();
    customizer.apply(source);
    return source;
  }

  private static Case postgis(Datasource source) throws Exception {
    return new Case(graphWithMapping(), source, postgisSink(), MAPPINGS);
  }

  private static Case postgisWithPk(Datasource source) throws Exception {
    return new Case(graphWithMapping(), source, postgisSinkWithPk(), MAPPINGS);
  }

  private static String encrypted(String plaintext, byte[] key) throws Exception {
    return "ENC("
        + CredentialEncryptor.encrypt(
            plaintext, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
        + ")";
  }

  private static Map<String, Object> mappingsWith(String mappingRef, String fieldsJson)
      throws Exception {
    Map<String, Object> mappings = new LinkedHashMap<>(MAPPINGS);
    mappings.put(mappingRef, map("{\"fields\": " + fieldsJson + "}"));
    return mappings;
  }
}
