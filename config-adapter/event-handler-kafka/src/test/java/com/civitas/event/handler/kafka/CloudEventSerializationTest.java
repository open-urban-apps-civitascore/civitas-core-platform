/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.event.handler.kafka;

import static org.junit.jupiter.api.Assertions.*;

import com.civitas.configadapter.Constants;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.idm.IdmConfigValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests to understand CloudEventSerializer and CloudEventDeserializer behavior. This helps debug
 * why EndToEndIntegrationTest receives NULL events.
 */
class CloudEventSerializationTest {

  private CloudEventSerializer serializer;
  private CloudEventDeserializer deserializer;

  @BeforeEach
  void setUp() {
    serializer = new CloudEventSerializer();
    deserializer = new CloudEventDeserializer();

    // Configure with empty maps (no special configuration)
    Map<String, Object> config = new HashMap<>();
    serializer.configure(config, false);
    deserializer.configure(config, false);
  }

  @Test
  void testSerializeDeserializeCloudEventWithoutData() {
    // Create CloudEvent without data - just like KeycloakAdapter does
    CloudEvent originalEvent =
        CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("test.source"))
            .withType("test.type")
            .withTime(OffsetDateTime.now())
            .withExtension("correlationid", "test-correlation-123")
            .withExtension("status", "SUCCESS")
            .withExtension("message", "Test message")
            .build();

    System.out.println("Original CloudEvent:");
    System.out.println("  ID: " + originalEvent.getId());
    System.out.println("  Source: " + originalEvent.getSource());
    System.out.println("  Type: " + originalEvent.getType());
    System.out.println("  Data: " + originalEvent.getData());
    System.out.println("  DataContentType: " + originalEvent.getDataContentType());
    System.out.println("  Extensions: " + originalEvent.getExtensionNames());

    // Serialize
    Headers headers = new RecordHeaders();
    byte[] serializedValue = serializer.serialize("test-topic", headers, originalEvent);

    System.out.println("\nAfter serialization:");
    System.out.println(
        "  Serialized value length: "
            + (serializedValue == null ? "NULL" : serializedValue.length));
    System.out.println(
        "  Serialized value: " + (serializedValue == null ? "NULL" : new String(serializedValue)));
    System.out.println("  Headers count: " + headers.toArray().length);
    headers.forEach(
        header -> {
          System.out.println("    " + header.key() + ": " + new String(header.value()));
        });

    // Deserialize
    CloudEvent deserializedEvent = deserializer.deserialize("test-topic", headers, serializedValue);

    System.out.println("\nDeserialized CloudEvent:");
    if (deserializedEvent == null) {
      System.out.println("  NULL!");
    } else {
      System.out.println("  ID: " + deserializedEvent.getId());
      System.out.println("  Source: " + deserializedEvent.getSource());
      System.out.println("  Type: " + deserializedEvent.getType());
      System.out.println("  Extensions: " + deserializedEvent.getExtensionNames());
      System.out.println("  correlationid: " + deserializedEvent.getExtension("correlationid"));
      System.out.println("  status: " + deserializedEvent.getExtension("status"));
    }

    // Assertions
    assertNotNull(deserializedEvent, "Deserialized event should not be null");
    assertEquals(originalEvent.getId(), deserializedEvent.getId());
    assertEquals(originalEvent.getSource(), deserializedEvent.getSource());
    assertEquals(originalEvent.getType(), deserializedEvent.getType());
    assertEquals("test-correlation-123", deserializedEvent.getExtension("correlationid"));
    assertEquals("SUCCESS", deserializedEvent.getExtension("status"));
  }

  @Test
  void testSerializeDeserializeCloudEventWithEmptyJsonData() {
    // Create CloudEvent with minimal empty JSON data
    CloudEvent originalEvent =
        CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("test.source"))
            .withType("test.type")
            .withTime(OffsetDateTime.now())
            .withData(Constants.CONTENT_TYPE_JSON, "{}".getBytes())
            .withExtension("correlationid", "test-correlation-456")
            .withExtension("status", "SUCCESS")
            .build();

    System.out.println("\nOriginal CloudEvent with data:");
    System.out.println("  ID: " + originalEvent.getId());
    System.out.println("  Data: " + new String(originalEvent.getData().toBytes()));
    System.out.println("  DataContentType: " + originalEvent.getDataContentType());

    // Serialize
    Headers headers = new RecordHeaders();
    byte[] serializedValue = serializer.serialize("test-topic", headers, originalEvent);

    System.out.println("\nAfter serialization:");
    System.out.println(
        "  Serialized value length: "
            + (serializedValue == null ? "NULL" : serializedValue.length));
    System.out.println(
        "  Serialized value: " + (serializedValue == null ? "NULL" : new String(serializedValue)));
    System.out.println("  Headers count: " + headers.toArray().length);
    headers.forEach(
        header -> {
          System.out.println("    " + header.key() + ": " + new String(header.value()));
        });

    // Deserialize
    CloudEvent deserializedEvent = deserializer.deserialize("test-topic", headers, serializedValue);

    System.out.println("\nDeserialized CloudEvent:");
    if (deserializedEvent == null) {
      System.out.println("  NULL!");
    } else {
      System.out.println("  ID: " + deserializedEvent.getId());
      System.out.println(
          "  Data: "
              + (deserializedEvent.getData() == null
                  ? "NULL"
                  : new String(deserializedEvent.getData().toBytes())));
      System.out.println("  correlationid: " + deserializedEvent.getExtension("correlationid"));
    }

    // Assertions
    assertNotNull(deserializedEvent, "Deserialized event should not be null");
    assertEquals(originalEvent.getId(), deserializedEvent.getId());
    assertNotNull(deserializedEvent.getData());
    assertEquals("test-correlation-456", deserializedEvent.getExtension("correlationid"));
  }

  @Test
  void testSerializeDeserializeCloudEventWithJsonData() {
    // Create CloudEvent with actual JSON data
    String jsonData = "{\"key\":\"value\",\"number\":42}";
    CloudEvent originalEvent =
        CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("test.source"))
            .withType("test.type")
            .withTime(OffsetDateTime.now())
            .withData(Constants.CONTENT_TYPE_JSON, jsonData.getBytes())
            .withExtension("correlationid", "test-correlation-789")
            .build();

    System.out.println("\nOriginal CloudEvent with JSON data:");
    System.out.println("  ID: " + originalEvent.getId());
    System.out.println("  Data: " + new String(originalEvent.getData().toBytes()));

    // Serialize
    Headers headers = new RecordHeaders();
    byte[] serializedValue = serializer.serialize("test-topic", headers, originalEvent);

    System.out.println("\nAfter serialization:");
    System.out.println("  Serialized value: " + new String(serializedValue));
    System.out.println("  Headers:");
    headers.forEach(
        header -> {
          System.out.println("    " + header.key() + ": " + new String(header.value()));
        });

    // Deserialize
    CloudEvent deserializedEvent = deserializer.deserialize("test-topic", headers, serializedValue);

    // Assertions
    assertNotNull(deserializedEvent);
    assertEquals(originalEvent.getId(), deserializedEvent.getId());
    assertNotNull(deserializedEvent.getData());
    assertEquals(jsonData, new String(deserializedEvent.getData().toBytes()));
  }

  @Test
  void testSerializeDeserializeCloudEventWithConfigResultEventData() throws Exception {
    ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    String correlationId = UUID.randomUUID().toString();
    String originalMessageId = UUID.randomUUID().toString();
    String resourceId = "user-12345";
    String targetResource = "users/user-12345";
    String source = "civitas.config-adapter.test";
    OffsetDateTime timestamp = OffsetDateTime.now();

    String resultType = IdmConfigValue.IDM_RESULT_TYPE;
    ConfigResultEvent resultEvent =
        new ConfigResultEvent(
            correlationId,
            originalMessageId,
            ConfigResultEvent.Status.SUCCESS,
            "User created successfully",
            resourceId,
            com.civitas.configadapter.model.Operation.CREATE,
            targetResource,
            null,
            timestamp,
            source,
            resultType);

    byte[] jsonData = objectMapper.writeValueAsBytes(resultEvent);

    CloudEvent originalEvent =
        CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create(source))
            .withType(IdmConfigValue.IDM_RESULT_TYPE)
            .withDataContentType(Constants.CONTENT_TYPE_JSON)
            .withData(jsonData)
            .withExtension("correlationid", correlationId)
            .withExtension("status", resultEvent.status().name())
            .build();

    Headers headers = new RecordHeaders();
    byte[] serializedValue = serializer.serialize("test-topic", headers, originalEvent);

    CloudEvent deserializedEvent = deserializer.deserialize("test-topic", headers, serializedValue);

    assertNotNull(deserializedEvent);
    assertEquals(originalEvent.getId(), deserializedEvent.getId());
    assertNotNull(deserializedEvent.getData());

    ConfigResultEvent deserializedResultEvent =
        objectMapper.readValue(deserializedEvent.getData().toBytes(), ConfigResultEvent.class);

    assertEquals(correlationId, deserializedResultEvent.correlationId());
    assertEquals(originalMessageId, deserializedResultEvent.originalMessageId());
    assertEquals(ConfigResultEvent.Status.SUCCESS, deserializedResultEvent.status());
    assertEquals("User created successfully", deserializedResultEvent.message());
    assertEquals(resourceId, deserializedResultEvent.resourceId());
    assertEquals(
        com.civitas.configadapter.model.Operation.CREATE, deserializedResultEvent.operation());
    assertEquals(targetResource, deserializedResultEvent.targetResource());
    assertEquals(source, deserializedResultEvent.source());
    assertNull(deserializedResultEvent.errorCode());
  }

  @Test
  void testSerializeDeserializeCloudEventWithFailureConfigResultEventData() throws Exception {
    ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    String correlationId = UUID.randomUUID().toString();
    String originalMessageId = UUID.randomUUID().toString();
    String targetResource = "users/user-99999";
    String source = "civitas.config-adapter.test";
    String errorCode = "USER_NOT_FOUND";
    String errorMessage = "User with ID user-99999 not found";

    ConfigResultEvent resultEvent =
        ConfigResultEvent.failure(
            correlationId,
            originalMessageId,
            errorCode,
            errorMessage,
            com.civitas.configadapter.model.Operation.DELETE,
            targetResource,
            source,
            IdmConfigValue.IDM_RESULT_TYPE);

    byte[] jsonData = objectMapper.writeValueAsBytes(resultEvent);

    CloudEvent originalEvent =
        CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create(source))
            .withType(IdmConfigValue.IDM_RESULT_TYPE)
            .withDataContentType(Constants.CONTENT_TYPE_JSON)
            .withData(jsonData)
            .withExtension("correlationid", correlationId)
            .withExtension("status", resultEvent.status().name())
            .withExtension("errorcode", errorCode)
            .withExtension("errormessage", errorMessage)
            .build();

    Headers headers = new RecordHeaders();
    byte[] serializedValue = serializer.serialize("test-topic", headers, originalEvent);

    CloudEvent deserializedEvent = deserializer.deserialize("test-topic", headers, serializedValue);

    assertNotNull(deserializedEvent);
    assertNotNull(deserializedEvent.getData());

    ConfigResultEvent deserializedResultEvent =
        objectMapper.readValue(deserializedEvent.getData().toBytes(), ConfigResultEvent.class);

    assertEquals(correlationId, deserializedResultEvent.correlationId());
    assertEquals(originalMessageId, deserializedResultEvent.originalMessageId());
    assertEquals(ConfigResultEvent.Status.FAILURE, deserializedResultEvent.status());
    assertEquals(errorMessage, deserializedResultEvent.message());
    assertNull(deserializedResultEvent.resourceId());
    assertEquals(
        com.civitas.configadapter.model.Operation.DELETE, deserializedResultEvent.operation());
    assertEquals(targetResource, deserializedResultEvent.targetResource());
    assertEquals(errorCode, deserializedResultEvent.errorCode());
    assertEquals(source, deserializedResultEvent.source());
  }
}
