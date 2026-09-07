package de.civitascore.portal.model.embedded;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;

/**
 * An enumeration of specific permissions that can be assigned to roles and users, defining the
 * actions they are authorized to perform within the system.
 *
 * @see de.civitascore.portal.model.entity.Permission
 */
@Getter
public enum PermissionName implements GrantedAuthority {
  // System administration.
  USER_CREATE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  USER_READ(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  USER_UPDATE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  USER_DELETE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),

  ROLE_CREATE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  ROLE_READ(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  ROLE_UPDATE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  ROLE_DELETE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),

  ASSIGNMENT_CREATE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  ASSIGNMENT_READ(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  ASSIGNMENT_DELETE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),

  GROUP_CREATE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  GROUP_READ(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  GROUP_UPDATE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),
  GROUP_DELETE(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),

  PERMISSION_READ(
      PermissionType.SYSTEM, PermissionCategory.TENANT_ADMINISTRATION, PermissionSource.INTERNAL),

  // Data access and management.
  DATASET_CREATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASET_READ(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  INSTALLATION_READ(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  INSTALLATION_DELETE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASET_UPDATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASET_DELETE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASET_RELEASE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),

  DATASET_PAYLOAD_CREATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASET_PAYLOAD_READ(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASET_PAYLOAD_UPDATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASET_PAYLOAD_DELETE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),

  DATASET_DASHBOARD_READ(
      PermissionType.DATA, PermissionCategory.DATA, PermissionSource.DATASET_DASHBOARD),
  DATASET_DASHBOARD_WRITE(
      PermissionType.DATA, PermissionCategory.DATA, PermissionSource.DATASET_DASHBOARD),

  DATASOURCE_CREATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASOURCE_READ(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASOURCE_UPDATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASOURCE_DELETE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASOURCE_RELEASE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),

  DATAPOOL_CREATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATAPOOL_READ(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATAPOOL_UPDATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATAPOOL_DELETE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),

  DATASTRUCTURE_CREATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASTRUCTURE_READ(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASTRUCTURE_UPDATE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASTRUCTURE_DELETE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL),
  DATASTRUCTURE_RELEASE(PermissionType.DATA, PermissionCategory.DATA, PermissionSource.INTERNAL);

  private final PermissionType permissionType;
  private final PermissionCategory category;
  private final PermissionSource source;

  PermissionName(
      PermissionType permissionType, PermissionCategory category, PermissionSource source) {
    this.permissionType = permissionType;
    this.category = category;
    this.source = source;
  }

  /** {@inheritDoc} Returns the enum constant name as the granted authority string. */
  @Override
  public String getAuthority() {
    return name();
  }
}
