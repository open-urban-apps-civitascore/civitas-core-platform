/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis.ddl;

import de.civitascore.configadapter.model.postgis.SchemaGrant;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure diff between the schema privileges a role <em>should</em> hold (from the event payload) and
 * the privileges it currently holds (read from the database). Produces the grants to add and the
 * privileges to revoke so the live state converges on the desired state.
 *
 * <p>{@link SchemaPrivilege#ALL} in the desired set expands to {@code USAGE} + {@code CREATE} so it
 * compares directly with what the database reports.
 */
public final class GrantReconciler {

  private GrantReconciler() {}

  /** A schema and the concrete privileges to revoke from it. */
  public record SchemaRevoke(String schema, List<SchemaPrivilege> privileges) {}

  /** The grants to add and the privileges to revoke to reach the desired state. */
  public record GrantReconcilePlan(List<SchemaGrant> toGrant, List<SchemaRevoke> toRevoke) {}

  /**
   * @param desired the grants from the role payload (empty when none requested)
   * @param current the privileges the role currently holds, keyed by schema
   * @return the grants to add and revokes to apply
   */
  public static GrantReconcilePlan reconcile(
      List<SchemaGrant> desired, Map<String, Set<SchemaPrivilege>> current) {
    Map<String, Set<SchemaPrivilege>> desiredBySchema = new LinkedHashMap<>();
    Map<String, Boolean> grantOptionBySchema = new LinkedHashMap<>();
    for (SchemaGrant grant : desired == null ? List.<SchemaGrant>of() : desired) {
      Set<SchemaPrivilege> expanded =
          desiredBySchema.computeIfAbsent(
              grant.schema(), s -> EnumSet.noneOf(SchemaPrivilege.class));
      expanded.addAll(expand(grant.effectivePrivileges()));
      grantOptionBySchema.merge(grant.schema(), grant.isWithGrantOption(), (a, b) -> a || b);
    }

    List<SchemaGrant> toGrant = new ArrayList<>();
    for (Map.Entry<String, Set<SchemaPrivilege>> entry : desiredBySchema.entrySet()) {
      String schema = entry.getKey();
      Set<SchemaPrivilege> missing = EnumSet.copyOf(entry.getValue());
      missing.removeAll(current.getOrDefault(schema, Set.of()));
      if (!missing.isEmpty()) {
        toGrant.add(
            new SchemaGrant(
                schema, sorted(missing), grantOptionBySchema.getOrDefault(schema, false)));
      }
    }

    List<SchemaRevoke> toRevoke = new ArrayList<>();
    for (Map.Entry<String, Set<SchemaPrivilege>> entry : current.entrySet()) {
      String schema = entry.getKey();
      Set<SchemaPrivilege> obsolete = EnumSet.copyOf(entry.getValue());
      obsolete.removeAll(desiredBySchema.getOrDefault(schema, Set.of()));
      if (!obsolete.isEmpty()) {
        toRevoke.add(new SchemaRevoke(schema, sorted(obsolete)));
      }
    }

    return new GrantReconcilePlan(toGrant, toRevoke);
  }

  private static Set<SchemaPrivilege> expand(List<SchemaPrivilege> privileges) {
    Set<SchemaPrivilege> result = EnumSet.noneOf(SchemaPrivilege.class);
    for (SchemaPrivilege privilege : privileges) {
      if (privilege == SchemaPrivilege.ALL) {
        result.add(SchemaPrivilege.USAGE);
        result.add(SchemaPrivilege.CREATE);
      } else {
        result.add(privilege);
      }
    }
    return result;
  }

  private static List<SchemaPrivilege> sorted(Set<SchemaPrivilege> privileges) {
    return new ArrayList<>(new TreeSet<>(privileges));
  }
}
