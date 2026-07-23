/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.civitascore.configadapter.flowable");
  }

  @Test
  void delegates_shouldNotDependOnKafkaPackage() {
    noClasses()
        .that()
        .resideInAPackage("..delegate..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..kafka..")
        .as("Delegates must be Kafka-agnostic — use bean map for publishing")
        .check(classes);
  }

  @Test
  void noClassesShouldDependOnCustomOrchestrator() {
    noClasses()
        .that()
        .resideInAPackage("..flowable..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..orchestrator..")
        .as("Flowable module must not depend on the custom orchestrator it replaces")
        .check(classes);
  }

  @Test
  void delegateImplementations_shouldEndWithDelegate() {
    classes()
        .that()
        .implement(org.flowable.engine.delegate.JavaDelegate.class)
        .should()
        .haveSimpleNameEndingWith("Delegate")
        .check(classes);
  }

  @Test
  void noCircularPackageDependencies() {
    slices()
        .matching("de.civitascore.configadapter.flowable.(*)..")
        .should()
        .beFreeOfCycles()
        .check(classes);
  }
}
