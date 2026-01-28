package de.civitascore.authz.repository.model.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Assignment entity for authorization context. Setters exist for JPA hydration and testing only -
 * the service layer is read-only (enforced via read-only DB connection).
 */
@Entity
@Table(name = "assignments")
@Getter
@Setter
public class Assignment {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "group_id")
  private Group group;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "role_id")
  private Role role;

  @Column(name = "scope_type")
  private String scopeType;

  @Column(name = "scope_id")
  private String scopeId;

  @Column(name = "is_inherited")
  private Boolean inherited;
}
