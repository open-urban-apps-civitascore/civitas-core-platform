package com.civitas.configadapter.messaging;

public interface EventConsumer extends AutoCloseable {

  void start();

  @Override
  default void close() {}
}
