package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Groups related {@link DataSet DataSets} into a series, allowing versioned or thematically linked
 * datasets to be tracked together.
 */
@Entity
@Table(name = "dataset_series")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class DataSetSeries extends NamedEntity {
  @OneToMany(mappedBy = "dataSetSeries", fetch = FetchType.LAZY)
  @Builder.Default
  private Set<DataSet> dataSets = new HashSet<>();
}
