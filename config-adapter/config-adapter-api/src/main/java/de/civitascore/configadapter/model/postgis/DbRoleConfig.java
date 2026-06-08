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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for a PostgreSQL role.
 *
 * <p>In PostgreSQL a database <em>user</em> is simply a role with the {@code LOGIN} attribute, so
 * this single type models both: set {@link #isCanLogin()} to create a login-capable role.
 *
 * <p>The optional {@link #getPassword()} may be an encrypted {@code ENC(...)} value following the
 * project credential convention; the adapter decrypts it with {@code CIVITAS_MASTER_KEY} before
 * issuing {@code CREATE/ALTER ROLE}. Plaintext passwords are never logged.
 *
 * <p>Schema-level {@link #getGrants() grants} are embedded. On UPDATE the adapter reconciles them
 * against the live database: privileges present in the payload are granted, privileges previously
 * granted but absent from the payload are revoked.
 *
 * <p>Example JSON:
 *
 * <pre>{@code
 * {
 *   "resourceType": "sql-role",
 *   "name": "analyst",
 *   "canLogin": true,
 *   "password": "ENC(...)",
 *   "grants": [
 *     {"schema": "iot", "privileges": ["USAGE"]}
 *   ]
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DbRoleConfig implements PostgisConfigValue {

  private String name;
  private boolean canLogin;
  private String password;
  private Boolean superuser;
  private Boolean createDb;
  private Boolean createRole;
  private Boolean inherit;
  private List<SchemaGrant> grants;

  public DbRoleConfig() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isCanLogin() {
    return canLogin;
  }

  public void setCanLogin(boolean canLogin) {
    this.canLogin = canLogin;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Boolean getSuperuser() {
    return superuser;
  }

  public void setSuperuser(Boolean superuser) {
    this.superuser = superuser;
  }

  public Boolean getCreateDb() {
    return createDb;
  }

  public void setCreateDb(Boolean createDb) {
    this.createDb = createDb;
  }

  public Boolean getCreateRole() {
    return createRole;
  }

  public void setCreateRole(Boolean createRole) {
    this.createRole = createRole;
  }

  public Boolean getInherit() {
    return inherit;
  }

  public void setInherit(Boolean inherit) {
    this.inherit = inherit;
  }

  public List<SchemaGrant> getGrants() {
    return grants == null ? List.of() : grants;
  }

  public void setGrants(List<SchemaGrant> grants) {
    this.grants = grants == null ? null : new ArrayList<>(grants);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    DbRoleConfig that = (DbRoleConfig) obj;
    return canLogin == that.canLogin
        && Objects.equals(this.name, that.name)
        && Objects.equals(this.password, that.password)
        && Objects.equals(this.superuser, that.superuser)
        && Objects.equals(this.createDb, that.createDb)
        && Objects.equals(this.createRole, that.createRole)
        && Objects.equals(this.inherit, that.inherit)
        && Objects.equals(this.grants, that.grants);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, canLogin, password, superuser, createDb, createRole, inherit, grants);
  }

  /** Never includes the password, so the role is safe to log. */
  @Override
  public String toString() {
    return "DbRoleConfig[name="
        + name
        + ", canLogin="
        + canLogin
        + ", password="
        + (password == null || password.isBlank() ? "<none>" : "***")
        + ", grants="
        + getGrants().size()
        + ']';
  }
}
