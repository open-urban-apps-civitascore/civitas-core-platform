/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.postgis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A single schema-level grant attached to a {@link DbRoleConfig}: the set of {@link SchemaPrivilege
 * privileges} the role holds on one schema.
 *
 * @param schema the schema name the privileges apply to
 * @param privileges the privileges granted; {@link SchemaPrivilege#ALL} expands to all schema
 *     privileges during reconciliation
 * @param withGrantOption whether the role may grant these privileges onward; {@code null} treated
 *     as {@code false}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchemaGrant(
    String schema, List<SchemaPrivilege> privileges, Boolean withGrantOption) {

  public List<SchemaPrivilege> effectivePrivileges() {
    return privileges == null ? List.of() : privileges;
  }

  public boolean isWithGrantOption() {
    return withGrantOption != null && withGrantOption;
  }
}
