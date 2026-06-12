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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.postgis.SchemaGrant;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.GrantReconcilePlan;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrantReconcilerTest {

  @Test
  void newSchemaGrantsAllDesiredPrivileges() {
    List<SchemaGrant> desired =
        List.of(new SchemaGrant("iot", List.of(SchemaPrivilege.USAGE), null));

    GrantReconcilePlan plan = GrantReconciler.reconcile(desired, Map.of());

    assertEquals(1, plan.toGrant().size());
    assertEquals("iot", plan.toGrant().get(0).schema());
    assertEquals(List.of(SchemaPrivilege.USAGE), plan.toGrant().get(0).effectivePrivileges());
    assertTrue(plan.toRevoke().isEmpty());
    assertTrue(plan.toRevokeGrantOption().isEmpty());
  }

  @Test
  void privilegeRemovedFromPayloadIsRevoked() {
    List<SchemaGrant> desired =
        List.of(new SchemaGrant("iot", List.of(SchemaPrivilege.USAGE), null));
    Map<String, Map<SchemaPrivilege, Boolean>> current =
        Map.of("iot", Map.of(SchemaPrivilege.USAGE, false, SchemaPrivilege.CREATE, false));

    GrantReconcilePlan plan = GrantReconciler.reconcile(desired, current);

    assertTrue(plan.toGrant().isEmpty());
    assertEquals(1, plan.toRevoke().size());
    assertEquals("iot", plan.toRevoke().get(0).schema());
    assertEquals(List.of(SchemaPrivilege.CREATE), plan.toRevoke().get(0).privileges());
  }

  @Test
  void unchangedPrivilegesProduceNoStatements() {
    List<SchemaGrant> desired =
        List.of(new SchemaGrant("iot", List.of(SchemaPrivilege.USAGE), null));
    Map<String, Map<SchemaPrivilege, Boolean>> current =
        Map.of("iot", Map.of(SchemaPrivilege.USAGE, false));

    GrantReconcilePlan plan = GrantReconciler.reconcile(desired, current);

    assertTrue(plan.toGrant().isEmpty());
    assertTrue(plan.toRevoke().isEmpty());
    assertTrue(plan.toRevokeGrantOption().isEmpty());
  }

  @Test
  void allExpandsToUsageAndCreateForComparison() {
    List<SchemaGrant> desired = List.of(new SchemaGrant("iot", List.of(SchemaPrivilege.ALL), null));
    Map<String, Map<SchemaPrivilege, Boolean>> current =
        Map.of("iot", Map.of(SchemaPrivilege.USAGE, false));

    GrantReconcilePlan plan = GrantReconciler.reconcile(desired, current);

    assertEquals(List.of(SchemaPrivilege.CREATE), plan.toGrant().get(0).effectivePrivileges());
    assertTrue(plan.toRevoke().isEmpty());
  }

  @Test
  void schemaDroppedFromPayloadRevokesAllItsPrivileges() {
    Map<String, Map<SchemaPrivilege, Boolean>> current =
        Map.of("iot", Map.of(SchemaPrivilege.USAGE, false, SchemaPrivilege.CREATE, false));

    GrantReconcilePlan plan = GrantReconciler.reconcile(List.of(), current);

    assertEquals(1, plan.toRevoke().size());
    assertEquals(
        List.of(SchemaPrivilege.USAGE, SchemaPrivilege.CREATE),
        plan.toRevoke().get(0).privileges());
  }

  @Test
  void withGrantOptionIsCarriedIntoGrant() {
    List<SchemaGrant> desired =
        List.of(new SchemaGrant("iot", List.of(SchemaPrivilege.USAGE), true));

    GrantReconcilePlan plan = GrantReconciler.reconcile(desired, Map.of());

    assertTrue(plan.toGrant().get(0).isWithGrantOption());
  }

  @Test
  void grantOptionRequestedOnHeldPrivilegeRegrantsWithGrantOption() {
    List<SchemaGrant> desired =
        List.of(new SchemaGrant("iot", List.of(SchemaPrivilege.USAGE), true));
    Map<String, Map<SchemaPrivilege, Boolean>> current =
        Map.of("iot", Map.of(SchemaPrivilege.USAGE, false));

    GrantReconcilePlan plan = GrantReconciler.reconcile(desired, current);

    assertEquals(1, plan.toGrant().size());
    assertEquals(List.of(SchemaPrivilege.USAGE), plan.toGrant().get(0).effectivePrivileges());
    assertTrue(plan.toGrant().get(0).isWithGrantOption());
    assertTrue(plan.toRevoke().isEmpty());
    assertTrue(plan.toRevokeGrantOption().isEmpty());
  }

  @Test
  void grantOptionDroppedFromPayloadIsRevokedWhilePrivilegeIsKept() {
    List<SchemaGrant> desired =
        List.of(new SchemaGrant("iot", List.of(SchemaPrivilege.USAGE), null));
    Map<String, Map<SchemaPrivilege, Boolean>> current =
        Map.of("iot", Map.of(SchemaPrivilege.USAGE, true));

    GrantReconcilePlan plan = GrantReconciler.reconcile(desired, current);

    assertTrue(plan.toGrant().isEmpty());
    assertTrue(plan.toRevoke().isEmpty());
    assertEquals(1, plan.toRevokeGrantOption().size());
    assertEquals("iot", plan.toRevokeGrantOption().get(0).schema());
    assertEquals(List.of(SchemaPrivilege.USAGE), plan.toRevokeGrantOption().get(0).privileges());
  }

  @Test
  void unchangedGrantOptionProducesNoStatements() {
    List<SchemaGrant> desired =
        List.of(new SchemaGrant("iot", List.of(SchemaPrivilege.USAGE), true));
    Map<String, Map<SchemaPrivilege, Boolean>> current =
        Map.of("iot", Map.of(SchemaPrivilege.USAGE, true));

    GrantReconcilePlan plan = GrantReconciler.reconcile(desired, current);

    assertTrue(plan.toGrant().isEmpty());
    assertTrue(plan.toRevoke().isEmpty());
    assertTrue(plan.toRevokeGrantOption().isEmpty());
  }
}
