package de.civitascore.portal.messaging.rabbitmq;

import com.civitas.configadapter.model.ConfigResultEvent;
import de.civitascore.portal.messaging.CloudEventPublisher;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.jackson.JsonFormat;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ implementation of CloudEventPublisher.
 *
 * <p>This is an example implementation showing how to create a RabbitMQ publisher. To use this, you
 * would need to:
 *
 * <ol>
 *   <li>Add spring-boot-starter-amqp dependency to pom.xml
 *   <li>Create a RabbitMQ configuration class similar to KafkaEventPublisherConfig
 *   <li>Configure RabbitMQ connection properties
 * </ol>
 *
 * <p>Example configuration bean:
 *
 * <pre>{@code
 * @Bean
 * @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
 * public CloudEventPublisher rabbitMQCloudEventPublisher(
 *     RabbitTemplate rabbitTemplate,
 *     RabbitMQConfigProperties properties) {
 *   return new RabbitMQCloudEventPublisher(rabbitTemplate, properties.getExchange());
 * }
 * }</pre>
 */
@Slf4j
public class RabbitMQCloudEventPublisher implements CloudEventPublisher {

  // Uncomment when adding RabbitMQ dependency:
  // private final RabbitTemplate rabbitTemplate;
  private final String exchange;
  private final String resultTopic;
  private final EventFormat jsonFormat;

  public RabbitMQCloudEventPublisher(Object rabbitTemplate, String exchange, String resultTopic) {
    // this.rabbitTemplate = (RabbitTemplate) rabbitTemplate;
    this.exchange = exchange;
    this.resultTopic = resultTopic;
    this.jsonFormat = new JsonFormat();
    log.info(
        "RabbitMQ CloudEvent publisher initialized for exchange: {} (resultTopic={})",
        exchange,
        resultTopic);
  }

  @Override
  public CompletableFuture<ConfigResultEvent> publishAsync(
      String topic, String messageId, CloudEvent cloudEvent) {
    try {
      // Convert CloudEvent to JSON bytes
      byte[] eventBytes = jsonFormat.serialize(cloudEvent);

      // Publish to RabbitMQ
      // rabbitTemplate.convertAndSend(
      //     exchange,
      //     topic, // routing key
      //     eventBytes,
      //     message -> {
      //       message.getMessageProperties().setContentType("application/cloudevents+json");
      //       message.getMessageProperties().setMessageId(messageId);
      //       return message;
      //     });

      log.debug(
          "CloudEvent published to RabbitMQ: exchange={}, routingKey={}, messageId={}",
          exchange,
          topic,
          messageId);

      return CompletableFuture.failedFuture(
          new UnsupportedOperationException(
              "RabbitMQ publisher is not fully implemented. "
                  + "Add spring-boot-starter-amqp dependency and uncomment the code."));

    } catch (Exception e) {
      return CompletableFuture.failedFuture(
          new PublishException(
              "Failed to publish CloudEvent to RabbitMQ: exchange="
                  + exchange
                  + ", routingKey="
                  + topic
                  + ", messageId="
                  + messageId,
              e));
    }
  }

  @Override
  public String getName() {
    return "rabbitmq";
  }

  @Override
  public boolean isReady() {
    // Check if RabbitTemplate connection is available
    // return rabbitTemplate.getConnectionFactory().createConnection().isOpen();
    return false; // Not implemented yet
  }

  @Override
  public String getResultTopic() {
    return resultTopic;
  }
}
