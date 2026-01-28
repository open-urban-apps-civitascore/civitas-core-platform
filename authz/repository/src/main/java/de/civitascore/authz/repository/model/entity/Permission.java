package de.civitascore.authz.repository.model.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Permission entity for authorization context. Setters exist for JPA hydration and testing only -
 * the service layer is read-only (enforced via read-only DB connection).
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission {

  @Id private UUID id;

  @Column(name = "name")
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "permission_type")
  private String permissionType;
}
