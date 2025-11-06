package com.civitas.configadapter.application;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.messaging.EventConsumer;

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

		String consumerClass = config.getConsumerClass();
		logger.info("Creating {} adapter(s) with consumer: {}", adapterClasses.size(), consumerClass);

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

				EventConsumer consumer = createConsumer(config, consumerClass, adapter);
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
		return (EventConsumer) consumerClazz.getConstructor(AppConfig.class, ConfigAdapter.class).newInstance(config,
				adapter);
	}

	private static ConfigAdapter createAdapter(AppConfig config, String adapterClass) throws ClassNotFoundException,
			InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Class<?> adapterClazz = Class.forName(adapterClass);
		return (ConfigAdapter) adapterClazz.getConstructor(AppConfig.class).newInstance(config);
	}
}
