package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an agent (person or system) that participates in {@link Activity Activities} and is
 * associated with {@link DataSet DataSets}.
 */
@Entity
@Table(name = "agents")
@Getter
@Setter
public class Agent extends BaseEntity {

  @NotBlank @Column(nullable = false)
  private String name;

  @ManyToMany(mappedBy = "agents", fetch = FetchType.LAZY)
  private Set<DataSet> dataSets = new HashSet<>();

  @ManyToMany(mappedBy = "agents", fetch = FetchType.LAZY)
  private Set<Activity> activities = new HashSet<>();
}
