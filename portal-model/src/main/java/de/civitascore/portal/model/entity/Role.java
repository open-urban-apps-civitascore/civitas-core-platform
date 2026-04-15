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
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a named role that bundles a set of {@link Permission Permissions}. The {@link
 * RoleType} determines whether the role applies at the system level or requires a data scope.
 *
 * @see RoleType
 * @see Assignment
 */
@Entity
@Table(
    name = "roles",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_role_name",
            columnNames = {"name"}),
    indexes = {@Index(name = "idx_role_type", columnList = "role_type")})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@NamedEntityGraph(
    name = "Role.withPermissions",
    attributeNodes = @NamedAttributeNode("permissions"))
public class Role extends NamedEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "role_type", nullable = false)
  @NotNull private RoleType roleType;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  @Builder.Default
  private Set<Permission> permissions = new HashSet<>();

  @Column(name = "readonly", nullable = false, updatable = false)
  @Builder.Default
  private boolean readonly = false;
}
