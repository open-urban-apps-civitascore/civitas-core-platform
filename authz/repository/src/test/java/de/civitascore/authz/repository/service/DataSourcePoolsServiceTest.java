package de.civitascore.authz.repository.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.authz.repository.data.DataSourceRepository;
import de.civitascore.authz.repository.model.dto.DataSourcePoolsResponse;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourcePoolsServiceTest {

  @Mock private DataSourceRepository dataSourceRepository;

  @InjectMocks private DataSourcePoolsService dataSourcePoolsService;

  private static final UUID DATA_SOURCE_ID = UUID.randomUUID();
  private static final UUID POOL_ID = UUID.randomUUID();

  private DataSource dataSource(DatapoolScopeType scopeType, UUID... scopedPoolIds) {
    DataSource dataSource = new DataSource();
    dataSource.setDatapoolScopeType(scopeType);
    Set<DataPool> pools =
        java.util.Arrays.stream(scopedPoolIds)
            .map(
                id -> {
                  DataPool pool = new DataPool();
                  pool.setId(id);
                  return pool;
                })
            .collect(java.util.stream.Collectors.toSet());
    dataSource.setScopedDataPools(pools);
    return dataSource;
  }

  @Nested
  @DisplayName("getDataSourcePools")
  class GetDataSourcePools {

    @Test
    @DisplayName("returns empty when the data source does not exist")
    void dataSourceNotFound_returnsEmpty() {
      when(dataSourceRepository.findById(DATA_SOURCE_ID)).thenReturn(Optional.empty());

      Optional<DataSourcePoolsResponse> result =
          dataSourcePoolsService.getDataSourcePools(DATA_SOURCE_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("an unrestricted data source is usable in every pool and names none")
    void scopeAll_isUsableInAllPools() {
      when(dataSourceRepository.findById(DATA_SOURCE_ID))
          .thenReturn(Optional.of(dataSource(DatapoolScopeType.ALL)));

      DataSourcePoolsResponse result =
          dataSourcePoolsService.getDataSourcePools(DATA_SOURCE_ID).orElseThrow();

      assertThat(result.isUsableInAllPools()).isTrue();
      assertThat(result.getPoolIds()).isEmpty();
    }

    @Test
    @DisplayName("an unrestricted data source keeps naming no pool even when it has scoped pools")
    void scopeAll_ignoresScopedPools() {
      when(dataSourceRepository.findById(DATA_SOURCE_ID))
          .thenReturn(Optional.of(dataSource(DatapoolScopeType.ALL, POOL_ID)));

      DataSourcePoolsResponse result =
          dataSourcePoolsService.getDataSourcePools(DATA_SOURCE_ID).orElseThrow();

      assertThat(result.isUsableInAllPools()).isTrue();
      assertThat(result.getPoolIds()).isEmpty();
    }

    @Test
    @DisplayName("a confined data source reports exactly the pools it names")
    void scopeSpecific_reportsScopedPools() {
      when(dataSourceRepository.findById(DATA_SOURCE_ID))
          .thenReturn(Optional.of(dataSource(DatapoolScopeType.SPECIFIC, POOL_ID)));

      DataSourcePoolsResponse result =
          dataSourcePoolsService.getDataSourcePools(DATA_SOURCE_ID).orElseThrow();

      assertThat(result.getPoolIds()).containsExactly(POOL_ID);
      assertThat(result.isUsableInAllPools()).isFalse();
    }

    @Test
    @DisplayName("a data source usable in no pipeline is usable in no pool either")
    void scopeNone_isUsableNowhere() {
      when(dataSourceRepository.findById(DATA_SOURCE_ID))
          .thenReturn(Optional.of(dataSource(DatapoolScopeType.NONE, POOL_ID)));

      DataSourcePoolsResponse result =
          dataSourcePoolsService.getDataSourcePools(DATA_SOURCE_ID).orElseThrow();

      assertThat(result.getPoolIds()).isEmpty();
      assertThat(result.isUsableInAllPools()).isFalse();
    }
  }
}
