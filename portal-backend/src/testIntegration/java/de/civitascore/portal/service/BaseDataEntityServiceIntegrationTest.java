package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Abstract base class for data entity service integration tests. Extends {@link
 * BaseServiceIntegrationTest} with assignment-specific tests.
 *
 * @param <E> the data entity type
 * @param <I> the data entity input DTO type
 */
public abstract class BaseDataEntityServiceIntegrationTest<
        E extends BaseDataEntity, I extends BaseDataEntityInputDTO>
    extends BaseServiceIntegrationTest<E, I> {

  @Override
  protected abstract BaseDataEntityService<E, I, ? super I> getService();

  /** Create an input DTO that includes assignment definitions. */
  protected abstract I createInputWithAssignments();

  @Test
  @DisplayName("Should create entity with assignments")
  void shouldCreateEntityWithAssignments() {
    E created = getService().create(createInputWithAssignments());

    assertThat(created.getAssignments()).isNotEmpty();
  }
}
