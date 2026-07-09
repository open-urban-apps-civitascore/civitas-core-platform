package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Abstract base class for service integration tests. Tests the {@link BaseService} CRUD contract.
 *
 * <p>Subclasses provide the service under test and factory methods for valid/update inputs plus
 * direct entity creation. The five contract tests verify create, read, update, delete, and
 * pagination.
 *
 * @param <E> the entity type
 * @param <I> the input DTO type
 */
public abstract class BaseServiceIntegrationTest<E extends BaseEntity, I extends BaseInputDTO>
    extends BaseKeycloakIntegrationTest {

  @Autowired protected PortalTestDataFactory portalData;

  protected abstract BaseService<E, I> getService();

  protected abstract I createValidInput();

  protected abstract I createUpdateInput();

  /** Create an entity directly via the repository, bypassing the service layer. */
  protected abstract E createTestEntity();

  /**
   * Optional hook for subclasses to perform additional cleanup before {@code portalData.cleanAll}.
   */
  protected void performAdditionalCleanup() {}

  @AfterEach
  void cleanupServiceTest() {
    performAdditionalCleanup();
    portalData.cleanAll();
  }

  @Test
  @DisplayName("Should create entity and retrieve it by ID")
  void shouldCreateAndRetrieveById() {
    E created = getService().create(createValidInput());

    E retrieved = getService().findByIdOrThrow(created.getId());

    assertThat(retrieved.getId()).isEqualTo(created.getId());
  }

  @Test
  @DisplayName("Should throw ResourceNotFoundException for non-existent ID")
  void shouldThrowResourceNotFoundForNonExistentId() {
    UUID randomId = UUID.randomUUID();

    assertThatThrownBy(() -> getService().findByIdOrThrow(randomId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("Should update existing entity")
  void shouldUpdateExistingEntity() {
    E created = getService().create(createValidInput());

    E updated = getService().update(created.getId(), createUpdateInput());

    assertThat(updated).isNotNull();
    assertThat(updated.getId()).isEqualTo(created.getId());
  }

  @Test
  @DisplayName("Should delete entity by ID")
  void shouldDeleteEntityById() {
    E created = getService().create(createValidInput());
    UUID id = created.getId();

    getService().deleteById(id);

    assertThatThrownBy(() -> getService().findByIdOrThrow(id))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("Should return paginated results")
  void shouldReturnPaginatedResults() {
    createTestEntity();
    createTestEntity();

    Page<E> page = getService().findAll(null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
  }
}
