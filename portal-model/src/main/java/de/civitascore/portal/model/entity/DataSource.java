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
@Table(name = "data_sources")
@Getter
@Setter
public class DataSource extends NamedEntity {
  @OneToMany(mappedBy = "dataSource", fetch = FetchType.LAZY)
  private Set<Assignment> assignments = new HashSet<>();
}
