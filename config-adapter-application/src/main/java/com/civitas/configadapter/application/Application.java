package com.civitas.configadapter.application;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.messaging.EventConsumer;
import com.civitas.configadapter.messaging.EventPublisher;

public class Application {

	private static final Logger logger = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) {
		logger.info("Starting Civitas Config Adapter...");

		AppConfig appConfig = new AppConfig("application.properties");

		List<EventConsumer> consumers = createConsumers(appConfig);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Shutdown signal received")));

		try {
			for (EventConsumer consumer : consumers) {
				consumer.start();
			}

			logger.info("Civitas Config Adapter is running with {} consumer(s). Press Ctrl+C to stop.",
					consumers.size());

			Thread.currentThread().join();

		} catch (InterruptedException e) {
			logger.error("Application interrupted", e);
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			logger.error("Fatal error in application", e);
			System.exit(1);
		} finally {
			logger.info("Shutting down {} consumer(s)", consumers.size());
			for (EventConsumer consumer : consumers) {
				try {
					consumer.close();
				} catch (Exception e) {
					logger.error("Error closing consumer", e);
				}
			}
		}

		logger.info("Application shutdown complete");
	}

	private static List<EventConsumer> createConsumers(AppConfig config) {
		List<String> adapterClasses = config.getAdapterClasses();

		if (adapterClasses.isEmpty()) {
			throw new RuntimeException("No adapters configured. Please specify 'adapters' or 'adapter.class' property");
		}

		// Determine configuration mode: combined or separate consumer/publisher
		String eventHandlerClass = config.getEventHandlerClass();
		String eventConsumerClass = config.getEventConsumerClass();
		String eventPublisherClass = config.getEventPublisherClass();

		if (eventHandlerClass != null && (eventConsumerClass != null || eventPublisherClass != null)) {
			throw new RuntimeException(
				"Configuration error: Cannot specify both 'eventhandler.class' and separate 'eventconsumer.class' or 'eventpublisher.class'");
		}

		boolean useCombinedHandler = eventHandlerClass != null;

		if (useCombinedHandler) {
			logger.info("Using combined event handler: {}", eventHandlerClass);
		} else {
			if (eventConsumerClass == null) {
				throw new RuntimeException("No event consumer configured. Please specify 'eventhandler.class' or 'eventconsumer.class'");
			}
			logger.info("Using separate consumer: {} and publisher: {}",
				eventConsumerClass, eventPublisherClass != null ? eventPublisherClass : "none");
		}

		logger.info("Creating {} adapter(s)", adapterClasses.size());

		List<EventConsumer> consumers = new ArrayList<>();

		for (String adapterClass : adapterClasses) {
			try {
				logger.info("Loading adapter: {}", adapterClass);

				ConfigAdapter adapter = createAdapter(config, adapterClass);
				List<String> topics = adapter.getSubscribedTopics();

				if (topics.isEmpty()) {
					logger.warn("Adapter {} has no subscribed topics, skipping", adapterClass);
					continue;
				}

				logger.info("Adapter {} subscribes to {} topic(s): {}", adapter.getClass().getSimpleName(),
						topics.size(), topics);

				EventConsumer consumer;
				if (useCombinedHandler) {
					// Combined handler: single class implements both EventConsumer and EventPublisher
					consumer = createConsumer(config, eventHandlerClass, adapter);
				} else {
					// Separate consumer and publisher
					EventPublisher publisher = null;
					if (eventPublisherClass != null) {
						publisher = createPublisher(config, eventPublisherClass);
					}

					// Inject publisher into adapter
					if (publisher != null) {
						adapter.setEventPublisher(publisher);
					}

					consumer = createConsumer(config, eventConsumerClass, adapter);
				}

				consumers.add(consumer);

				logger.info("Successfully created consumer for adapter: {}", adapterClass);

			} catch (Exception e) {
				logger.error("Failed to create consumer for adapter: {}", adapterClass, e);
				throw new RuntimeException("Failed to create consumer for adapter: " + adapterClass, e);
			}
		}

		if (consumers.isEmpty()) {
			throw new RuntimeException("No valid consumers created. Check adapter topic configurations.");
		}

		return consumers;
	}

	private static EventConsumer createConsumer(AppConfig config, String consumerClass, ConfigAdapter adapter)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException,
			NoSuchMethodException {
		Class<?> consumerClazz = Class.forName(consumerClass);

		// Verify that the class implements EventConsumer interface
		if (!EventConsumer.class.isAssignableFrom(consumerClazz)) {
			throw new IllegalArgumentException(
				String.format("Class %s does not implement EventConsumer interface", consumerClass));
		}

		return (EventConsumer) consumerClazz.getConstructor(AppConfig.class, ConfigAdapter.class).newInstance(config,
				adapter);
	}

	private static EventPublisher createPublisher(AppConfig config, String publisherClass)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException,
			NoSuchMethodException {
		Class<?> publisherClazz = Class.forName(publisherClass);

		// Verify that the class implements EventPublisher interface
		if (!EventPublisher.class.isAssignableFrom(publisherClazz)) {
			throw new IllegalArgumentException(
				String.format("Class %s does not implement EventPublisher interface", publisherClass));
		}

		return (EventPublisher) publisherClazz.getConstructor(AppConfig.class).newInstance(config);
	}

	private static ConfigAdapter createAdapter(AppConfig config, String adapterClass) throws ClassNotFoundException,
			InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Class<?> adapterClazz = Class.forName(adapterClass);

		// Verify that the class implements ConfigAdapter interface
		if (!ConfigAdapter.class.isAssignableFrom(adapterClazz)) {
			throw new IllegalArgumentException(
				String.format("Class %s does not implement ConfigAdapter interface", adapterClass));
		}

		return (ConfigAdapter) adapterClazz.getConstructor(AppConfig.class).newInstance(config);
	}
}
