package de.civitascore.portal.model.embedded;

/** Discriminator values carried in the {@code type} field of saga result messages. */
public enum SagaResultType {
  SAGA_COMPLETED,
  SAGA_FAILED
}
