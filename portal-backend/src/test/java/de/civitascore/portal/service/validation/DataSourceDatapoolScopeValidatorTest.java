package de.civitascore.portal.service.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DataSourceDatapoolScopeValidator")
class DataSourceDatapoolScopeValidatorTest {

  private final DataSourceDatapoolScopeValidator validator = new DataSourceDatapoolScopeValidator();

  private DataPool pool(UUID id) {
    DataPool p = new DataPool();
    p.setId(id);
    return p;
  }

  private DataSource source(DatapoolScopeType type, DataPool... scopedPools) {
    DataSource ds = new DataSource();
    ds.setId(UUID.randomUUID());
    ds.setDatapoolScopeType(type);
    ds.setScopedDataPools(new HashSet<>(Set.of(scopedPools)));
    return ds;
  }

  @Test
  @DisplayName("ALL-scoped is permitted for any pool")
  void allScopedPermittedForPool() {
    DataPool pool = pool(UUID.randomUUID());
    assertThatCode(() -> validator.validate(List.of(source(DatapoolScopeType.ALL)), pool))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("ALL-scoped is permitted for a pool-less dataset")
  void allScopedPermittedForPoolLess() {
    assertThatCode(() -> validator.validate(List.of(source(DatapoolScopeType.ALL)), null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("NONE-scoped is never permitted, with or without a pool")
  void noneScopedNeverPermitted() {
    DataSource none = source(DatapoolScopeType.NONE);
    assertThatThrownBy(() -> validator.validate(List.of(none), pool(UUID.randomUUID())))
        .isInstanceOf(DataSourceScopeViolationException.class);
    assertThatThrownBy(() -> validator.validate(List.of(none), null))
        .isInstanceOf(DataSourceScopeViolationException.class);
  }

  @Test
  @DisplayName("SPECIFIC-scoped is permitted only for a pool in its scopedDataPools")
  void specificScopedPermittedOnlyForScopedPool() {
    DataPool poolA = pool(UUID.randomUUID());
    DataPool poolB = pool(UUID.randomUUID());
    DataSource specificToA = source(DatapoolScopeType.SPECIFIC, poolA);

    assertThatCode(() -> validator.validate(List.of(specificToA), poolA))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> validator.validate(List.of(specificToA), poolB))
        .isInstanceOf(DataSourceScopeViolationException.class);
  }

  @Test
  @DisplayName("SPECIFIC-scoped is never permitted for a pool-less dataset")
  void specificScopedNeverPermittedForPoolLess() {
    DataSource specific = source(DatapoolScopeType.SPECIFIC, pool(UUID.randomUUID()));
    assertThatThrownBy(() -> validator.validate(List.of(specific), null))
        .isInstanceOf(DataSourceScopeViolationException.class);
  }

  @Test
  @DisplayName("collects every offending id across scope types")
  void collectsAllOffendingIds() {
    DataSource none = source(DatapoolScopeType.NONE);
    DataSource specificMismatch = source(DatapoolScopeType.SPECIFIC, pool(UUID.randomUUID()));
    DataPool target = pool(UUID.randomUUID());

    assertThatThrownBy(() -> validator.validate(List.of(none, specificMismatch), target))
        .isInstanceOf(DataSourceScopeViolationException.class)
        .satisfies(
            ex ->
                assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                    .containsExactlyInAnyOrder(none.getId(), specificMismatch.getId()));
  }
}
