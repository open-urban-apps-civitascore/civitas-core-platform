/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architecture constraints for the NiFi adapter. */
@AnalyzeClasses(
    packages = "de.civitascore.configadapter.nifi",
    importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

  /**
   * Security invariant: tenant transforms must be expressible in RecordPath only. No part of the
   * adapter may pull in Jolt, scripting, or a JVM scripting engine — those are arbitrary-code
   * vectors Nila Löber's analysis ruled out for 2.0.
   */
  @ArchTest
  static final ArchRule noScriptingOrJolt =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..jolt..", "..bazaarvoice..", "..groovy..", "javax.script..", "..nashorn..")
          .because("NiFi flows must use RecordPath only — no Jolt and no in-band scripting");

  /** The mapping layer is pure transformation logic — it must not reach into HTTP/REST concerns. */
  @ArchTest
  static final ArchRule mappingLayerIsTransportFree =
      noClasses()
          .that()
          .resideInAPackage("..nifi.mapping..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..nifi.rest..", "jakarta.ws.rs..", "org.glassfish..");

  /**
   * The REST client only deploys a pre-built {@code DeploymentPlan}; parsing and flow assembly are
   * the planner's job, so the transport layer must not reach back into them.
   */
  @ArchTest
  static final ArchRule restLayerStaysTransportOnly =
      noClasses()
          .that()
          .resideInAPackage("..nifi.rest..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..nifi.mapping..", "..nifi.graph..")
          .because("the REST client must consume only the finished DeploymentPlan");

  /** Credential handling stays free of any transport concern. */
  @ArchTest
  static final ArchRule credentialsLayerIsTransportFree =
      noClasses()
          .that()
          .resideInAPackage("..nifi.credentials..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..nifi.rest..", "jakarta.ws.rs..");

  /** No package may take part in a dependency cycle. */
  @ArchTest
  static final ArchRule noPackageCycles =
      slices().matching("de.civitascore.configadapter.nifi.(*)..").should().beFreeOfCycles();
}
