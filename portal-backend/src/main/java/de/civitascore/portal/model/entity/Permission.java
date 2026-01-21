package de.civitascore.portal.model.entity;

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

@Entity
@Table(
    name = "permissions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_permission_name",
            columnNames = {"name"}),
    indexes = {
      @Index(name = "idx_permission_type", columnList = "permission_type"),
    })
@Getter
@Setter
public class Permission extends NamedEntity implements GrantedAuthority {

  @Enumerated(EnumType.STRING)
  @Column(name = "permission_type", nullable = false)
  private PermissionType permissionType;

  @Column(name = "category", nullable = false)
  private String category;

  @Override
  public String getAuthority() {
    return getName();
  }
}
