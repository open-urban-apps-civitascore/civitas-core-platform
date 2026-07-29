/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

/**
 * NiFi Expression Language escaping for tenant-supplied text that lands in an EL-enabled processor
 * property.
 *
 * <p>Several PutDatabaseRecord/UpdateRecord properties evaluate Expression Language, so a tenant
 * value carrying {@code ${ENV_VAR}} would otherwise expand against the NiFi process environment at
 * write time and exfiltrate into the tenant's data. Any tenant-controlled string that reaches such
 * a property — a mapping value, a table name, an update key — must pass through {@link #escape}
 * first. This is the single source of that escape so the value and identifier paths cannot drift.
 */
public final class NifiExpressionLanguage {

  private NifiExpressionLanguage() {}

  /**
   * Escapes {@code $} to EL's literal form {@code $$} so the value is treated as text, never
   * evaluated. Idempotent only for already-escaped input is <em>not</em> guaranteed — apply exactly
   * once, at the boundary where tenant text becomes a processor-property value.
   */
  public static String escape(String value) {
    return value.replace("$", "$$");
  }
}
