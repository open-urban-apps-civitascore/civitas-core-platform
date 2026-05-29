package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
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
 * Represents a downloadable or addressable resource that is exposed through one or more {@link
 * Distribution Distributions}.
 *
 * <p><b>Dormant:</b> currently has no production writer or controller — reserved for the deferred
 * DCAT distribution work. See {@link Distribution} for the dormancy contract.
 */
@Entity
@Table(name = "resources")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Resource extends BaseEntity {
  @OneToMany(mappedBy = "resource", fetch = FetchType.LAZY)
  @Builder.Default
  private Set<Distribution> distributions = new HashSet<>();
}
