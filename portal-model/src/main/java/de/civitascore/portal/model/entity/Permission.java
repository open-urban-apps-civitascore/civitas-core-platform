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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

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
@EqualsAndHashCode(callSuper = true)
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

  @Override
  public String getAuthority() {
    return getName();
  }
}
