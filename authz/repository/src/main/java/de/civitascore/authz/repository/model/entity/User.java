package de.civitascore.authz.repository.model.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * User entity for authorization context. Setters exist for JPA hydration and testing only - the
 * service layer is read-only (enforced via read-only DB connection).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

  @Id private UUID id;

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(name = "email")
  private String email;

  @Column(name = "active")
  private Boolean active;

  @ManyToMany
  @JoinTable(
      name = "group_members",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "group_id"))
  private Set<Group> groups = new HashSet<>();
}
