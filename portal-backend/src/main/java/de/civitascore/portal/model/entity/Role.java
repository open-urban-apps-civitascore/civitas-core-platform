package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "roles",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_role_name_tenant",
            columnNames = {"name", "tenant_id"}),
    indexes = {
      @Index(name = "idx_role_type", columnList = "role_type"),
      @Index(name = "idx_role_default", columnList = "is_default")
    })
@Getter
@Setter
@NamedEntityGraph(
    name = "Role.withPermissions",
    attributeNodes = @NamedAttributeNode("permissions"))
public class Role extends NamedEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "role_type", nullable = false)
  private RoleType roleType;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<Permission> permissions = new HashSet<>();

  @Column(name = "is_default", nullable = false)
  private Boolean isDefault = false;

  @Column(name = "user_modifiable", nullable = false)
  private Boolean userModifiable = true;
}
