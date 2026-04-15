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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents an agent (person or system) that participates in {@link Activity Activities} and is
 * associated with {@link DataSet DataSets}.
 */
@Entity
@Table(name = "agents")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agent extends BaseEntity {

  @NotBlank @Column(nullable = false)
  private String name;

  @ManyToMany(mappedBy = "agents", fetch = FetchType.LAZY)
  @Builder.Default
  private Set<DataSet> dataSets = new HashSet<>();

  @ManyToMany(mappedBy = "agents", fetch = FetchType.LAZY)
  @Builder.Default
  private Set<Activity> activities = new HashSet<>();
}
