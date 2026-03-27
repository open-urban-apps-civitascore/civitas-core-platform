package de.civitascore.portal.model.embedded;

import lombok.Getter;

/** Standard role definitions as described in the Authorization Data Model documentation. */
@Getter
public enum RoleDefault {
  TENANT_ADMIN(
      "Tenant Admin",
      "Tenant-wide permissions to manage users, roles and permissions as well as tenant"
          + " parameters.",
      RoleType.SYSTEM,
      new PermissionName[] {
        PermissionName.USER_CREATE,
        PermissionName.USER_READ,
        PermissionName.USER_UPDATE,
        PermissionName.USER_DELETE,
        PermissionName.ROLE_CREATE,
        PermissionName.ROLE_READ,
        PermissionName.ROLE_UPDATE,
        PermissionName.ROLE_DELETE,
        PermissionName.ASSIGNMENT_CREATE,
        PermissionName.ASSIGNMENT_READ,
        PermissionName.ASSIGNMENT_DELETE,
        PermissionName.GROUP_CREATE,
        PermissionName.GROUP_READ,
        PermissionName.GROUP_UPDATE,
        PermissionName.GROUP_DELETE,
        PermissionName.PERMISSION_READ,
      }),

  DATA_ARCHITECT(
      "Data Architect",
      "Management rights for all aspects of data ingestion, data processing, data storage and"
          + " data output.",
      RoleType.DATA,
      new PermissionName[] {
        PermissionName.DATASET_CREATE,
        PermissionName.DATASET_READ,
        PermissionName.DATASET_UPDATE,
        PermissionName.DATASET_DELETE,
        PermissionName.DATASOURCE_CREATE,
        PermissionName.DATASOURCE_READ,
        PermissionName.DATASOURCE_UPDATE,
        PermissionName.DATASOURCE_DELETE,
        PermissionName.DATASTRUCTURE_CREATE,
        PermissionName.DATASTRUCTURE_READ,
        PermissionName.DATASTRUCTURE_UPDATE,
        PermissionName.DATASTRUCTURE_DELETE,
      }),

  DATA_CONSUMER(
      "Data Consumer",
      "Read access to published data products.",
      RoleType.DATA,
      new PermissionName[] {
        PermissionName.DATASET_READ, PermissionName.DATASET_PAYLOAD_READ,
      }),

  DATA_STEWARD(
      "Data Steward",
      "Responsibility for the life-cycle of domain specific data in a data space.",
      RoleType.DATA,
      new PermissionName[] {
        PermissionName.DATASET_CREATE,
        PermissionName.DATASET_READ,
        PermissionName.DATASET_UPDATE,
        PermissionName.DATASET_DELETE,
        PermissionName.DATASET_PAYLOAD_CREATE,
        PermissionName.DATASET_PAYLOAD_READ,
        PermissionName.DATASET_PAYLOAD_UPDATE,
        PermissionName.DATASET_PAYLOAD_DELETE,
        PermissionName.DATASOURCE_CREATE,
        PermissionName.DATASOURCE_READ,
        PermissionName.DATASOURCE_UPDATE,
        PermissionName.DATASOURCE_DELETE,
        PermissionName.DATASTRUCTURE_CREATE,
        PermissionName.DATASTRUCTURE_READ,
        PermissionName.DATASTRUCTURE_UPDATE,
        PermissionName.DATASTRUCTURE_DELETE,
      }),

  DATA_OWNER(
      "Data Owner",
      "Business responsibility for one or several domains.",
      RoleType.DATA,
      new PermissionName[] {
        PermissionName.DATASET_CREATE,
        PermissionName.DATASET_READ,
        PermissionName.DATASET_UPDATE,
        PermissionName.DATASET_DELETE,
        PermissionName.DATASET_RELEASE,
        PermissionName.DATASET_PAYLOAD_CREATE,
        PermissionName.DATASET_PAYLOAD_READ,
        PermissionName.DATASET_PAYLOAD_UPDATE,
        PermissionName.DATASET_PAYLOAD_DELETE,
        PermissionName.DATASOURCE_CREATE,
        PermissionName.DATASOURCE_READ,
        PermissionName.DATASOURCE_UPDATE,
        PermissionName.DATASOURCE_DELETE,
        PermissionName.DATASOURCE_RELEASE,
        PermissionName.DATASTRUCTURE_CREATE,
        PermissionName.DATASTRUCTURE_READ,
        PermissionName.DATASTRUCTURE_UPDATE,
        PermissionName.DATASTRUCTURE_DELETE,
        PermissionName.DATASTRUCTURE_RELEASE,
      }),

  DATA_GATEKEEPER(
      "Data Gatekeeper",
      "Responsibility for data protection and data governance.",
      RoleType.DATA,
      new PermissionName[] {
        PermissionName.DATASET_READ,
        PermissionName.DATASET_RELEASE,
        PermissionName.DATASET_PAYLOAD_READ,
        PermissionName.DATASOURCE_READ,
        PermissionName.DATASOURCE_RELEASE,
        PermissionName.DATASTRUCTURE_READ,
        PermissionName.DATASTRUCTURE_RELEASE,
      });

  final String roleName;
  final String description;
  final RoleType roleType;
  final PermissionName[] permissions;

  RoleDefault(
      String roleName, String description, RoleType roleType, PermissionName[] permissions) {
    this.roleName = roleName;
    this.description = description;
    this.roleType = roleType;
    this.permissions = permissions;
  }
}
