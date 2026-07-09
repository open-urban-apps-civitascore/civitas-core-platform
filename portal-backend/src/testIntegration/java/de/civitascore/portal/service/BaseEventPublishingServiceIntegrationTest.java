package de.civitascore.portal.service;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Abstract base class for event publishing service integration tests. Provides shared
 * infrastructure (test data factory, cleanup) for concrete tests in {@code service/event/}.
 *
 * <p>No test methods — concrete event publishing tests already cover Kafka behavior thoroughly.
 *
 * @param <E> the entity type
 * @param <I> the input DTO type
 */
public abstract class BaseEventPublishingServiceIntegrationTest<
        E extends BaseEntity, I extends BaseInputDTO>
    extends BaseKeycloakIntegrationTest {

  @Autowired protected PortalTestDataFactory portalData;

  protected abstract EventPublishingService<E, I> getService();

  /**
   * Optional hook for subclasses to perform additional cleanup before {@code portalData.cleanAll}.
   */
  protected void performAdditionalCleanup() {}

  @AfterEach
  void cleanupEventTest() {
    performAdditionalCleanup();
    portalData.cleanAll();
  }
}
