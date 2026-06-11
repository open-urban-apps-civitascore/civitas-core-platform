/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.geoserver;

import de.civitascore.configadapter.model.AbstractApiModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration value for a GeoServer data store. Focused on PostGIS connections; connection
 * parameters are flattened into this object and serialized to the GeoServer entry-list format.
 *
 * <p>Produces the GeoServer REST API JSON:
 *
 * <pre>{@code
 * {
 *   "dataStore": {
 *     "name": "civitas_postgis",
 *     "description": "PostGIS data source",
 *     "type": "PostGIS",
 *     "enabled": true,
 *     "connectionParameters": {
 *       "entry": [
 *         {"@key": "host",     "$": "localhost"},
 *         {"@key": "port",     "$": "5432"},
 *         {"@key": "database", "$": "civitas_geo"},
 *         {"@key": "schema",   "$": "public"},
 *         {"@key": "user",     "$": "geo_user"},
 *         {"@key": "passwd",   "$": "secret"},
 *         {"@key": "dbtype",   "$": "postgis"}
 *       ]
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Jackson discriminator: {@code "resourceType": "geoserver-datastore"}
 */
public final class DataStoreConfig extends AbstractApiModel implements GeoServerConfigValue {

  private String name;
  private String description;
  private String type = "PostGIS";
  private Boolean enabled;

  // PostGIS connection parameters
  private String host;
  private String port;
  private String database;
  private String schema;
  private String user;
  private String passwd;
  // Kept as String while PostGIS is the only supported database. Once a second dbtype is added,
  // turn this into an enum so the supported values are validated and self-documenting.
  private String dbtype = "postgis";
  private Boolean exposePrimaryKeys;

  public DataStoreConfig() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getPort() {
    return port;
  }

  public void setPort(String port) {
    this.port = port;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getPasswd() {
    return passwd;
  }

  public void setPasswd(String passwd) {
    this.passwd = passwd;
  }

  public String getDbtype() {
    return dbtype;
  }

  public void setDbtype(String dbtype) {
    this.dbtype = dbtype;
  }

  public Boolean getExposePrimaryKeys() {
    return exposePrimaryKeys;
  }

  public void setExposePrimaryKeys(Boolean exposePrimaryKeys) {
    this.exposePrimaryKeys = exposePrimaryKeys;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> ds = new LinkedHashMap<>();
    if (name != null) ds.put("name", name);
    if (description != null) ds.put("description", description);
    if (type != null) ds.put("type", type);
    if (enabled != null) ds.put("enabled", enabled);

    List<Map<String, Object>> entries = new ArrayList<>();
    addEntry(entries, "host", host);
    addEntry(entries, "port", port);
    addEntry(entries, "database", database);
    addEntry(entries, "schema", schema);
    addEntry(entries, "user", user);
    addEntry(entries, "passwd", passwd);
    addEntry(entries, "dbtype", dbtype);
    if (exposePrimaryKeys != null) {
      addEntry(entries, "Expose primary keys", String.valueOf(exposePrimaryKeys));
    }
    if (!entries.isEmpty()) {
      ds.put("connectionParameters", Map.of("entry", entries));
    }

    additionalProperties().forEach(ds::putIfAbsent);
    return Map.of("dataStore", Collections.unmodifiableMap(ds));
  }

  private static void addEntry(List<Map<String, Object>> entries, String key, String value) {
    if (value != null) {
      entries.add(Map.of("@key", key, "$", value));
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (DataStoreConfig) obj;
    return Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(type, that.type)
        && Objects.equals(enabled, that.enabled)
        && Objects.equals(host, that.host)
        && Objects.equals(port, that.port)
        && Objects.equals(database, that.database)
        && Objects.equals(schema, that.schema)
        && Objects.equals(user, that.user)
        && Objects.equals(passwd, that.passwd)
        && Objects.equals(dbtype, that.dbtype)
        && Objects.equals(exposePrimaryKeys, that.exposePrimaryKeys)
        && Objects.equals(additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        description,
        type,
        enabled,
        host,
        port,
        database,
        schema,
        user,
        passwd,
        dbtype,
        exposePrimaryKeys,
        additionalProperties());
  }

  @Override
  public String toString() {
    return "DataStoreConfig[name="
        + name
        + ", type="
        + type
        + ", host="
        + host
        + ", database="
        + database
        + ']';
  }
}
