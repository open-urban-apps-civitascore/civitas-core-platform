package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.PermissionCategory;
import de.civitascore.portal.model.embedded.PermissionSource;
import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

/**
 * Represents a single permission that can be granted to a {@link Role}. Implements {@link
 * GrantedAuthority} so that permissions integrate directly with Spring Security.
 *
 * @see PermissionType
 * @see PermissionCategory
 * @see PermissionSource
 */
@Entity
@Table(
    name = "permissions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_permission_name_source",
            columnNames = {"name", "source"}),
    indexes = {
      @Index(name = "idx_permission_type", columnList = "permission_type"),
    })
@Getter
@Setter
public class Permission extends NamedEntity implements GrantedAuthority {

  @Enumerated(EnumType.STRING)
  @Column(name = "permission_type", nullable = false)
  private PermissionType permissionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private PermissionCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false)
  private PermissionSource source;

  /** {@inheritDoc} Returns the permission name as the granted authority string. */
  @Override
  public String getAuthority() {
    return getName();
  }
}
