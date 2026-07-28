/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

/**
 * The closed set of curated NiFi component fragments — the only processors and controller services
 * the adapter can ever emit. Being an enum, no string (tenant-derived or otherwise) can reach the
 * classpath loader; adding a component is an explicit, reviewable constant.
 *
 * <p>The resource name is also part of the deterministic component-id seed, so renaming one changes
 * every derived UUID and breaks redeploy idempotency against a live NiFi.
 */
public enum Fragment {
  CONSUME_MQTT("consume_mqtt"),
  QUERY_DATABASE_TABLE_RECORD("query_database_table_record"),
  CONVERT_RECORD("convert_record"),
  UPDATE_RECORD("update_record"),
  FORK_RECORD("fork_record"),
  PUT_DATABASE_RECORD("put_database_record"),
  INVOKE_HTTP("invoke_http"),
  LOG_MESSAGE("log_message"),
  SPLIT_JSON("split_json"),
  EVALUATE_JSON_PATH("evaluate_json_path"),
  REPLACE_TEXT("replace_text"),
  ROUTE_ON_ATTRIBUTE("route_on_attribute"),
  JSON_TREE_READER("json_tree_reader"),
  JSON_RECORD_SET_WRITER("json_record_set_writer"),
  DBCP_CONNECTION_POOL("dbcp_connection_pool");

  private final String resource;

  Fragment(String resource) {
    this.resource = resource;
  }

  /** The classpath resource base name under {@code fragments/}. */
  public String resource() {
    return resource;
  }
}
