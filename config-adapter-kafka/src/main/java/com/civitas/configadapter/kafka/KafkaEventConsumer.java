package com.civitas.configadapter.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.core.CloudEventProcessor;
import com.civitas.configadapter.messaging.EventConsumer;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;

/**
 * Kafka implementation of EventConsumer.
 * Consumes CloudEvents from Kafka topics and processes them through the adapter.
 */
public class KafkaEventConsumer implements EventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private static final String KAFKA_GROUP_ID = "kafka.group.id";
    private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

    private final KafkaConsumer<String, CloudEvent> kafkaConsumer;
    private final ConfigAdapter adapter;
    private final CloudEventProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;

    public KafkaEventConsumer(AppConfig config, ConfigAdapter adapter) {
        this.adapter = adapter;
        this.processor = new CloudEventProcessor(adapter);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getProperty(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getProperty(KAFKA_GROUP_ID, "config-adapter-group"));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");

        this.kafkaConsumer = new KafkaConsumer<>(props);

        List<String> topicList = new ArrayList<>(adapter.getSubscribedTopics());
        kafkaConsumer.subscribe(topicList);

        logger.info("Kafka event consumer initialized for adapter {} with {} topic(s): {}",
            adapter.getClass().getSimpleName(), topicList.size(), topicList);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            consumerThread = Thread.ofVirtual().start(this::consume);
            logger.info("Kafka event consumer started");
        }
    }

    private void consume() {
        logger.info("Starting consumption loop");
        while (running.get()) {
            try {
                ConsumerRecords<String, CloudEvent> records = kafkaConsumer.poll(Duration.ofMillis(1000));

                records.forEach(record -> {
                    try {
                        processor.handleEvent(record.topic(), record.value());
                    } catch (Exception e) {
                        logger.error("Error handling CloudEvent: {}", record.value().getId(), e);
                    }
                });
            } catch (Exception e) {
                logger.error("Error consuming messages", e);
                if (!running.get()) {
                    break;
                }
            }
        }
        logger.info("Consumption loop ended");
    }

    public void stop() {
        logger.info("Stopping Kafka event consumer");
        running.set(false);
        if (consumerThread != null) {
            try {
                consumerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        stop();
        try {
            kafkaConsumer.close();
            logger.info("Kafka consumer closed");
        } catch (Exception e) {
            logger.error("Error closing Kafka consumer", e);
        }
        try {
            adapter.close();
            logger.info("Adapter closed");
        } catch (Exception e) {
            logger.error("Error closing adapter", e);
        }
    }
}
