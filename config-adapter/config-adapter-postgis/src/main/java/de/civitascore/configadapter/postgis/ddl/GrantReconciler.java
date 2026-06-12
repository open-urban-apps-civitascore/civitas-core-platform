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
 * the privileges it currently holds (read from the database). Produces the grants to add, the
 * privileges to revoke, and the grant options to revoke so the live state converges on the desired
 * state.
 *
 * <p>{@link SchemaPrivilege#ALL} in the desired set expands to {@code USAGE} + {@code CREATE} so it
 * compares directly with what the database reports.
 *
 * <p>The grant option is reconciled per privilege: a privilege already held without the grant
 * option is re-granted {@code WITH GRANT OPTION} when the payload requests it (PostgreSQL upgrades
 * the existing grant in place), and a privilege held grantable is downgraded via {@code REVOKE
 * GRANT OPTION FOR} when the payload no longer requests it. Fully obsolete privileges are revoked
 * outright, which removes their grant option as well.
 */
public final class GrantReconciler {

  private GrantReconciler() {}

  /** A schema and the concrete privileges to revoke (or strip the grant option) from it. */
  public record SchemaRevoke(String schema, List<SchemaPrivilege> privileges) {}

  /** The grants to add and the (grant-option) revokes to apply to reach the desired state. */
  public record GrantReconcilePlan(
      List<SchemaGrant> toGrant,
      List<SchemaRevoke> toRevoke,
      List<SchemaRevoke> toRevokeGrantOption) {}

  /**
   * @param desired the grants from the role payload (empty when none requested)
   * @param current the privileges the role currently holds, keyed by schema; the inner map carries
   *     whether each privilege is held {@code WITH GRANT OPTION}
   * @return the grants to add, privileges to revoke, and grant options to strip
   */
  public static GrantReconcilePlan reconcile(
      List<SchemaGrant> desired, Map<String, Map<SchemaPrivilege, Boolean>> current) {
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
    List<SchemaRevoke> toRevokeGrantOption = new ArrayList<>();
    for (Map.Entry<String, Set<SchemaPrivilege>> entry : desiredBySchema.entrySet()) {
      String schema = entry.getKey();
      boolean wantOption = grantOptionBySchema.getOrDefault(schema, false);
      Map<SchemaPrivilege, Boolean> held = current.getOrDefault(schema, Map.of());

      Set<SchemaPrivilege> needsGrant = EnumSet.noneOf(SchemaPrivilege.class);
      Set<SchemaPrivilege> optionObsolete = EnumSet.noneOf(SchemaPrivilege.class);
      for (SchemaPrivilege privilege : entry.getValue()) {
        Boolean grantable = held.get(privilege);
        if (grantable == null || (wantOption && !grantable)) {
          // not held at all, or held without the requested grant option — (re-)grant
          needsGrant.add(privilege);
        } else if (!wantOption && grantable) {
          optionObsolete.add(privilege);
        }
      }
      if (!needsGrant.isEmpty()) {
        toGrant.add(new SchemaGrant(schema, sorted(needsGrant), wantOption));
      }
      if (!optionObsolete.isEmpty()) {
        toRevokeGrantOption.add(new SchemaRevoke(schema, sorted(optionObsolete)));
      }
    }

    List<SchemaRevoke> toRevoke = new ArrayList<>();
    for (Map.Entry<String, Map<SchemaPrivilege, Boolean>> entry : current.entrySet()) {
      String schema = entry.getKey();
      Set<SchemaPrivilege> obsolete = EnumSet.noneOf(SchemaPrivilege.class);
      obsolete.addAll(entry.getValue().keySet());
      obsolete.removeAll(desiredBySchema.getOrDefault(schema, Set.of()));
      if (!obsolete.isEmpty()) {
        toRevoke.add(new SchemaRevoke(schema, sorted(obsolete)));
      }
    }

    return new GrantReconcilePlan(toGrant, toRevoke, toRevokeGrantOption);
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
