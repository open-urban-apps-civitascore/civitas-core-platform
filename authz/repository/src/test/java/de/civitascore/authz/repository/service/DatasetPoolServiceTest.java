package de.civitascore.authz.repository.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.authz.repository.data.DataSetRepository;
import de.civitascore.authz.repository.model.dto.DatasetPoolResponse;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetPoolServiceTest {

  @Mock private DataSetRepository dataSetRepository;

  @InjectMocks private DatasetPoolService datasetPoolService;

  private static final UUID DATASET_ID = UUID.randomUUID();
  private static final UUID POOL_ID = UUID.randomUUID();

  @Nested
  @DisplayName("getDatasetPool")
  class GetDatasetPool {

    @Test
    @DisplayName("returns empty when the dataset does not exist")
    void datasetNotFound_returnsEmpty() {
      when(dataSetRepository.findById(DATASET_ID)).thenReturn(Optional.empty());

      Optional<DatasetPoolResponse> result = datasetPoolService.getDatasetPool(DATASET_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns the pool id when the dataset belongs to a pool")
    void datasetWithPool_returnsPoolId() {
      DataPool pool = new DataPool();
      pool.setId(POOL_ID);
      DataSet dataSet = new DataSet();
      dataSet.setDataPool(pool);
      when(dataSetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataSet));

      Optional<DatasetPoolResponse> result = datasetPoolService.getDatasetPool(DATASET_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getPoolId()).isEqualTo(POOL_ID);
    }

    @Test
    @DisplayName("returns null pool id when the dataset has no pool")
    void datasetWithoutPool_returnsNullPoolId() {
      DataSet dataSet = new DataSet();
      dataSet.setDataPool(null);
      when(dataSetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataSet));

      Optional<DatasetPoolResponse> result = datasetPoolService.getDatasetPool(DATASET_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getPoolId()).isNull();
    }

    @Test
    @DisplayName("returns openDataAccess=true when the dataset is flagged for open data")
    void datasetFlaggedOpen_returnsOpenDataAccessTrue() {
      DataSet dataSet = new DataSet();
      dataSet.setOpenDataAccess(true);
      when(dataSetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataSet));

      Optional<DatasetPoolResponse> result = datasetPoolService.getDatasetPool(DATASET_ID);

      assertThat(result).isPresent();
      assertThat(result.get().isOpenDataAccess()).isTrue();
    }

    @Test
    @DisplayName("returns openDataAccess=false when the dataset is not flagged open")
    void datasetNotFlaggedOpen_returnsOpenDataAccessFalse() {
      DataSet dataSet = new DataSet();
      dataSet.setOpenDataAccess(false);
      when(dataSetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataSet));

      Optional<DatasetPoolResponse> result = datasetPoolService.getDatasetPool(DATASET_ID);

      assertThat(result).isPresent();
      assertThat(result.get().isOpenDataAccess()).isFalse();
    }

    @Test
    @DisplayName("defaults openDataAccess to false when the flag is unset (secure default)")
    void datasetUnsetFlag_defaultsToFalse() {
      DataSet dataSet = new DataSet();
      when(dataSetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataSet));

      Optional<DatasetPoolResponse> result = datasetPoolService.getDatasetPool(DATASET_ID);

      assertThat(result).isPresent();
      assertThat(result.get().isOpenDataAccess()).isFalse();
    }
  }
}
