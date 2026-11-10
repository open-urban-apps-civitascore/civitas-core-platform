/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.adapter.DashboardExporter;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportException;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportRequest;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult.Reason;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportTopics;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers dashboard export requests from Kafka through the {@link DashboardExporter} of a saga
 * handler. It runs inside config-adapter, because the export needs the dashboard tool's admin
 * session, which only config-adapter holds. Exports run one at a time on the poll thread: they are
 * rare and take seconds. Auto-commit may replay a few seconds after a crash; the replayed requests
 * have expired by then and are dropped.
 */
final class DashboardExportConsumer implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(DashboardExportConsumer.class);

  static final String UNEXPECTED_FAILURE = "The export failed on the side of the platform.";

  private final Consumer<String, String> consumer;
  private final Producer<String, String> producer;
  private final String requestTopic;
  private final String resultTopic;
  private final DashboardExporter exporter;
  private final Clock clock;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private volatile boolean running;
  private final AtomicBoolean closed = new AtomicBoolean();
  private Thread pollThread;

  DashboardExportConsumer(
      Consumer<String, String> consumer,
      Producer<String, String> producer,
      String requestTopic,
      String resultTopic,
      DashboardExporter exporter,
      Clock clock) {
    this.consumer = consumer;
    this.producer = producer;
    this.requestTopic = requestTopic;
    this.resultTopic = resultTopic;
    this.exporter = exporter;
    this.clock = clock;
  }

  /** Builds the consumer from the {@code dashboardexport.*} properties; it is not yet started. */
  static DashboardExportConsumer create(AdapterConfig config, DashboardExporter exporter) {
    String bootstrap = config.getProperty("kafka.bootstrap.servers", "localhost:9092");
    String groupId =
        config.getProperty("dashboardexport.kafka.group.id", "config-adapter-dashboard-export");
    String requestTopic =
        config.getProperty("dashboardexport.kafka.request-topic", DashboardExportTopics.REQUEST);
    String resultTopic =
        config.getProperty("dashboardexport.kafka.result-topic", DashboardExportTopics.RESULT);
    LOG.info(
        "Dashboard export: group {}, topics {} -> {}",
        Encode.forJava(groupId),
        Encode.forJava(requestTopic),
        Encode.forJava(resultTopic));
    return new DashboardExportConsumer(
        new KafkaConsumer<>(consumerProperties(bootstrap, groupId)),
        new KafkaProducer<>(producerProperties(bootstrap)),
        requestTopic,
        resultTopic,
        exporter,
        Clock.systemUTC());
  }

  /** Starts polling on a virtual thread and returns at once. */
  void start() {
    if (running) {
      throw new IllegalStateException("DashboardExportConsumer is already running");
    }
    consumer.subscribe(List.of(requestTopic));
    running = true;
    pollThread = Thread.ofVirtual().name("dashboard-export-consumer").start(this::pollLoop);
    LOG.info("Dashboard export consumer started, subscribed to {}", Encode.forJava(requestTopic));
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException") // Loop must survive transient poll errors
  private void pollLoop() {
    while (running) {
      try {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
          handle(record);
        }
      } catch (WakeupException e) {
        // close() calls wakeup(); while still running it is unexpected and must propagate.
        if (running) {
          throw e;
        }
        return;
      } catch (RuntimeException e) {
        if (running) {
          LOG.error("Error in dashboard export consumer poll loop, continuing", e);
        }
      }
    }
  }

  void handle(ConsumerRecord<String, String> record) {
    DashboardExportRequest request;
    try {
      request = mapper.readValue(record.value(), DashboardExportRequest.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      LOG.warn(
          "Skipping a malformed dashboard export request at {}-{}@{}: {}",
          record.topic(),
          record.partition(),
          record.offset(),
          e.getClass().getSimpleName());
      return;
    }
    String requestId = request.requestId();
    if (requestId == null || requestId.isBlank()) {
      LOG.warn("Dropping a dashboard export request without a request id");
      return;
    }
    if (request.expiresAt() == null || !clock.instant().isBefore(request.expiresAt())) {
      // The requester has stopped waiting, so a result would reach nobody.
      LOG.info("Dropping expired dashboard export request {}", Encode.forJava(requestId));
      return;
    }
    publish(export(request));
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException") // A failed export must not stop the loop
  DashboardExportResult export(DashboardExportRequest request) {
    long begin = System.nanoTime();
    DashboardExportResult result;
    try {
      result =
          DashboardExportResult.success(
              request.requestId(), exporter.exportDashboard(request.externalDashboardId()));
    } catch (DashboardExportException e) {
      result = DashboardExportResult.failure(request.requestId(), e.reason(), e.getMessage());
    } catch (RuntimeException e) {
      // The text of an unexpected failure can name platform internals; it stays in this log.
      LOG.error("Dashboard export {} failed unexpectedly", Encode.forJava(request.requestId()), e);
      result =
          DashboardExportResult.failure(
              request.requestId(), Reason.UNAVAILABLE, UNEXPECTED_FAILURE);
    }
    LOG.info(
        "Dashboard export {} of dashboard {} ended with {} {} after {} ms",
        Encode.forJava(request.requestId()),
        Encode.forJava(String.valueOf(request.externalDashboardId())),
        result.outcome(),
        result.reason() == null ? "" : result.reason(),
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
    return result;
  }

  private void publish(DashboardExportResult result) {
    String json;
    try {
      json = mapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      LOG.error(
          "Serializing the result of dashboard export {} failed",
          Encode.forJava(result.requestId()),
          e);
      return;
    }
    producer.send(
        new ProducerRecord<>(resultTopic, result.requestId(), json),
        (metadata, exception) -> {
          if (exception != null) {
            LOG.error(
                "Publishing the result of dashboard export {} failed",
                Encode.forJava(result.requestId()),
                exception);
          }
        });
  }

  @Override
  // try-with-resources would close the producer without a time limit.
  @SuppressWarnings("PMD.UseTryWithResources")
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    running = false;
    consumer.wakeup();
    if (pollThread != null) {
      try {
        pollThread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    try {
      consumer.close();
    } finally {
      producer.close(Duration.ofSeconds(5));
    }
    LOG.info("Dashboard export consumer stopped");
  }

  private static Properties consumerProperties(String bootstrap, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    // A new group starts at the end: a request from before its start has no caller left.
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    // One export at a time; a small batch keeps the requests behind it from expiring in the queue.
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "5");
    return props;
  }

  private static Properties producerProperties(String bootstrap) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    return props;
  }
}
