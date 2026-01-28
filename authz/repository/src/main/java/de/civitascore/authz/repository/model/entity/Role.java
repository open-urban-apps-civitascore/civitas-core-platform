package de.civitascore.authz.repository.model.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Role entity for authorization context. Setters exist for JPA hydration and testing only - the
 * service layer is read-only (enforced via read-only DB connection).
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role {

  @Id private UUID id;

  @Column(name = "name")
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "role_type")
  private String roleType;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<Permission> permissions = new HashSet<>();
}
