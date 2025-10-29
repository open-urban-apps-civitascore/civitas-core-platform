package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

@Entity
@Table(
    name = "permissions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_permission_name_tenant",
            columnNames = {"title", "tenant_id"}),
    indexes = {
      @Index(name = "idx_permission_type", columnList = "permission_type"),
      @Index(name = "idx_permission_modifiable", columnList = "user_modifiable")
    })
@Getter
@Setter
public class Permission extends NamedEntity<String> implements GrantedAuthority {

  @Enumerated(EnumType.STRING)
  @Column(name = "permission_type", nullable = false)
  private PermissionType permissionType;

  @Column(name = "user_modifiable", nullable = false)
  private Boolean userModifiable = false;

  @Column(name = "is_default", nullable = false)
  private Boolean isDefault = false;

  @Override
  public String getAuthority() {
    return getTitle();
  }
}
