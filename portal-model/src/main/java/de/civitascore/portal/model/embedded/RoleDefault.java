package de.civitascore.portal.model.embedded;

import java.util.List;
import lombok.Getter;

/** Standard role definitions as described in the Authorization Data Model documentation. */
@Getter
public enum RoleDefault {
  TENANT_ADMIN(
      "Tenant Admin",
      "Mandantenweite Berechtigungen zur Verwaltung von Usern, Rollen und Berechtigungen"
          + " sowie Mandantenparametern.",
      RoleType.SYSTEM,
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
      PermissionName.PERMISSION_READ),

  DATA_ARCHITECT(
      "Data Architect",
      "Verwaltungsrechte für alle Aspekte der Datenaufnahme, Datenverarbeitung, Datenspeicherung"
          + " und Datenausgabe.",
      RoleType.DATA,
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
      PermissionName.DATAPOOL_READ,
      PermissionName.DATAPOOL_CREATE,
      PermissionName.DATAPOOL_UPDATE,
      PermissionName.DATAPOOL_DELETE,
      PermissionName.INSTALLATION_READ),

  DATA_CONSUMER(
      "Data Consumer",
      "Lesezugriff auf veröffentlichte Datenprodukte.",
      RoleType.DATA,
      PermissionName.DATASET_READ,
      PermissionName.DATASET_PAYLOAD_READ,
      PermissionName.DATAPOOL_READ),

  DATA_STEWARD(
      "Data Steward",
      "Verantwortung für den Lebenszyklus domänenspezifischer Daten.",
      RoleType.DATA,
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
      PermissionName.DATAPOOL_READ,
      PermissionName.DATAPOOL_UPDATE),

  DATA_OWNER(
      "Data Owner",
      "Fachliche Verantwortung für eine oder mehrere Domänen.",
      RoleType.DATA,
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
      PermissionName.DATAPOOL_READ,
      PermissionName.DATAPOOL_UPDATE),

  DATA_GATEKEEPER(
      "Data Gatekeeper",
      "Verantwortung für Datenschutz und Daten-Governance.",
      RoleType.DATA,
      PermissionName.DATASET_READ,
      PermissionName.DATASET_RELEASE,
      PermissionName.DATASET_PAYLOAD_READ,
      PermissionName.DATASOURCE_READ,
      PermissionName.DATASOURCE_RELEASE,
      PermissionName.DATASTRUCTURE_READ,
      PermissionName.DATASTRUCTURE_RELEASE,
      PermissionName.DATAPOOL_READ);

  final String roleName;
  final String description;
  final RoleType roleType;
  final List<PermissionName> permissions;

  RoleDefault(
      String roleName, String description, RoleType roleType, PermissionName... permissions) {
    this.roleName = roleName;
    this.description = description;
    this.roleType = roleType;
    this.permissions = List.of(permissions);
  }
}
