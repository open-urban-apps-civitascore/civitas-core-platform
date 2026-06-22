/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.Constants;
import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.ColumnType;
import de.civitascore.configadapter.model.postgis.GeometryColumnConfig;
import de.civitascore.configadapter.model.postgis.GeometryType;
import de.civitascore.configadapter.model.postgis.IndexConfig;
import de.civitascore.configadapter.model.postgis.IndexConfig.IndexMethod;
import de.civitascore.configadapter.model.postgis.PostgisConfigValue;
import de.civitascore.configadapter.model.postgis.TableConfig;
import de.civitascore.event.handler.kafka.KafkaEventHandler;
import de.civitascore.event.handler.kafka.ObjectMapperFactory;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end pipeline test: produces a CloudEvent to a Kafka topic, lets the real {@link
 * KafkaEventHandler} pass it to the real {@link PostgisAdapter}, and verifies both the published
 * result event and the table physically present in PostGIS.
 *
 * <p>The PostGIS container is contributed by {@link AbstractPostgisIT} as a JVM-singleton; Kafka is
 * a per-class container managed by Testcontainers JUnit.
 */
@Testcontainers
class PostgisEndToEndIT extends AbstractPostgisIT {

  private static final String RESULT_TOPIC = "result.topic";

  @SuppressWarnings("resource")
  @Container
  static ConfluentKafkaContainer kafka =
      new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"))
          .withReuse(false);

  private PostgisAdapter adapter;
  private KafkaEventHandler eventHandler;
  private KafkaProducer<String, CloudEvent> producer;
  private KafkaConsumer<String, CloudEvent> resultConsumer;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    objectMapper = ObjectMapperFactory.createObjectMapper();

    Map<String, Object> props = new HashMap<>();
    props.put("kafka.bootstrap.servers", kafka.getBootstrapServers());
    props.put("kafka.group.id", "postgis-e2e-" + UUID.randomUUID());
    props.put("kafka.auto.offset.reset", "earliest");
    props.put("postgis.jdbc.url", jdbcUrl());
    props.put("postgis.jdbc.user", username());
    props.put("postgis.jdbc.password", password());
    props.put("postgis.jdbc.maxPoolSize", "2");
    props.put("postgis.jdbc.connectionTimeoutMs", "5000");
    props.put(
        "postgis.topics",
        String.join(
            ",",
            Topics.SQL_TABLE_CREATED.toString(),
            Topics.SQL_TABLE_UPDATED.toString(),
            Topics.SQL_TABLE_DELETED.toString()));
    AppConfig appConfig = new AppConfig(new MapConfiguration(props));

    adapter = new PostgisAdapter();
    adapter.initialize(appConfig);

    eventHandler = new KafkaEventHandler();
    eventHandler.initialize(appConfig, adapter);
    eventHandler.start();

    producer = newCloudEventProducer();
    resultConsumer = newCloudEventConsumer("postgis-result-" + UUID.randomUUID());
    resultConsumer.subscribe(Collections.singletonList(RESULT_TOPIC));
    resultConsumer.poll(Duration.ofMillis(100));
  }

  @AfterEach
  void tearDown() {
    if (eventHandler != null) {
      eventHandler.close();
    }
    if (adapter != null) {
      adapter.close();
    }
    if (producer != null) {
      producer.close();
    }
    if (resultConsumer != null) {
      resultConsumer.close();
    }
  }

  @Test
  void createTableEndToEndProducesSuccessAndTableExistsInDatabase() throws Exception {
    TableConfig table = buildSensorReadingsTable();
    String correlationId = "create-e2e-" + UUID.randomUUID();

    sendCreateEvent(table, correlationId);

    CloudEvent resultEvent = waitForResultEvent(correlationId, Duration.ofSeconds(20));
    assertNotNull(resultEvent, "Result event should arrive on the result topic");
    assertEquals("SUCCESS", resultEvent.getExtension("status"));
    assertEquals(PostgisConfigValue.POSTGIS_RESULT_TYPE, resultEvent.getType());

    ConfigResultEvent result =
        objectMapper.readValue(resultEvent.getData().toBytes(), ConfigResultEvent.class);
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals(Operation.CREATE, result.operation());
    assertEquals(correlationId, result.correlationId());

    assertTrue(
        tableExists("e2e", "sensor_readings"),
        "Table e2e.sensor_readings should physically exist in PostGIS after the pipeline runs");
  }

  @Test
  void deleteTableEndToEndRemovesTableFromDatabase() throws Exception {
    executeSql("CREATE TABLE \"to_drop_e2e\" (id BIGINT)");
    TableConfig table = new TableConfig();
    table.setName("to_drop_e2e");
    String correlationId = "delete-e2e-" + UUID.randomUUID();

    CloudEvent cloudEvent =
        wrapInCloudEvent(buildConfigEvent(table, Operation.DELETE, correlationId));
    producer
        .send(new ProducerRecord<>(Topics.SQL_TABLE_DELETED.toString(), "key", cloudEvent))
        .get();
    producer.flush();

    CloudEvent resultEvent = waitForResultEvent(correlationId, Duration.ofSeconds(20));
    assertNotNull(resultEvent);
    assertEquals("SUCCESS", resultEvent.getExtension("status"));
    assertEquals(
        false, tableExists(null, "to_drop_e2e"), "to_drop_e2e should be gone after DELETE");
  }

  // --- Helpers ---

  private void sendCreateEvent(TableConfig table, String correlationId) throws Exception {
    CloudEvent cloudEvent =
        wrapInCloudEvent(buildConfigEvent(table, Operation.CREATE, correlationId));
    producer
        .send(new ProducerRecord<>(Topics.SQL_TABLE_CREATED.toString(), "key", cloudEvent))
        .get();
    producer.flush();
  }

  private static TableConfig buildSensorReadingsTable() {
    TableConfig table = new TableConfig();
    table.setSchema("e2e");
    table.setName("sensor_readings");
    table.setColumns(
        List.of(
            new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false),
            new ColumnConfig("recorded_at", ColumnType.TIMESTAMPTZ, null, null, null, false),
            new ColumnConfig("temperature", ColumnType.NUMERIC, null, 6, 2, true)));
    table.setGeometryColumns(
        List.of(new GeometryColumnConfig("location", GeometryType.POINT, 4326, null, false)));
    table.setPrimaryKey(List.of("id"));
    table.setIndexes(
        List.of(
            new IndexConfig(
                "idx_sensor_readings_location", List.of("location"), false, IndexMethod.GIST)));
    return table;
  }

  private ConfigEvent buildConfigEvent(
      TableConfig table, Operation operation, String correlationId) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "postgis-e2e",
            correlationId,
            "1.0",
            RESULT_TOPIC);
    Payload payload =
        new Payload(
            PostgisAdapter.ADAPTER_NAME, table.qualifiedName(), operation, new Config(null, table));
    return new ConfigEvent(metadata, payload);
  }

  private CloudEvent wrapInCloudEvent(ConfigEvent configEvent) throws Exception {
    byte[] jsonBytes = objectMapper.writeValueAsBytes(configEvent);
    return CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(URI.create("postgis.e2e.test"))
        .withType("de.civitascore.data.sql.table.created")
        .withDataContentType(Constants.CONTENT_TYPE_JSON)
        .withData(jsonBytes)
        .build();
  }

  private CloudEvent waitForResultEvent(String correlationId, Duration timeout) {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (System.currentTimeMillis() < deadline) {
      ConsumerRecords<String, CloudEvent> records = resultConsumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, CloudEvent> record : records) {
        CloudEvent event = record.value();
        if (event != null && correlationId.equals(event.getExtension("correlationid"))) {
          return event;
        }
      }
    }
    return null;
  }

  private KafkaProducer<String, CloudEvent> newCloudEventProducer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
    return new KafkaProducer<>(props);
  }

  private KafkaConsumer<String, CloudEvent> newCloudEventConsumer(String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new KafkaConsumer<>(props);
  }
}
