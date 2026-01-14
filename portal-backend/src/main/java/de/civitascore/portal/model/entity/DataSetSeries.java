package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dataset_series")
@Getter
@Setter
public class DataSetSeries extends NamedEntity {
  @OneToMany(mappedBy = "dataSetSeries", fetch = FetchType.LAZY)
  private Set<DataSet> dataSets = new HashSet<>();
}
