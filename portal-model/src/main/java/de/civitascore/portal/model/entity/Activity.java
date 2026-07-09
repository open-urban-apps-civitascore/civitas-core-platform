package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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
 * Represents an activity that groups {@link Agent}s and produces {@link Distribution}s within the
 * data catalog.
 *
 * <p><b>Dormant:</b> currently has no production writer or controller — reserved for the deferred
 * DCAT distribution work. See {@link Distribution} for the dormancy contract.
 */
@Entity
@Table(name = "activities")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Activity extends NamedEntity {

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "activity_agents",
      joinColumns = @JoinColumn(name = "activity_id"),
      inverseJoinColumns = @JoinColumn(name = "agent_id"),
      indexes = {
        @Index(name = "idx_activity_agents_activity", columnList = "activity_id"),
        @Index(name = "idx_activity_agents_agent", columnList = "agent_id")
      })
  @Builder.Default
  private Set<Agent> agents = new HashSet<>();

  @OneToMany(mappedBy = "activity", fetch = FetchType.LAZY)
  @Builder.Default
  private Set<Distribution> distributions = new HashSet<>();
}
