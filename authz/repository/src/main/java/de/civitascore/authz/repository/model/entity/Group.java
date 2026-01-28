package de.civitascore.authz.repository.model.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Group entity for authorization context. Setters exist for JPA hydration and testing only - the
 * service layer is read-only (enforced via read-only DB connection).
 */
@Entity
@Table(name = "groups")
@Getter
@Setter
public class Group {

  @Id private UUID id;

  @Column(name = "name")
  private String name;

  @Column(name = "description")
  private String description;

  @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
  private Set<Assignment> assignments = new HashSet<>();
}
