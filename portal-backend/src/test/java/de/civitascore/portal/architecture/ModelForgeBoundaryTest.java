package de.civitascore.portal.architecture;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Guards the anti-corruption boundary around embedded Model Forge: every access to Model Forge
 * types must go through the {@code de.civitascore.portal.modelregistry} package (the {@code
 * ModelRegistryGateway} and its supporting types). The rest of the application works with host
 * types only, so Model Forge's contract can evolve without rippling through the codebase.
 */
@AnalyzeClasses(packages = "de.civitascore.portal", importOptions = DoNotIncludeTests.class)
class ModelForgeBoundaryTest {

  @ArchTest
  static final ArchRule only_the_modelregistry_gateway_uses_model_forge =
      ArchRuleDefinition.noClasses()
          .that()
          .resideOutsideOfPackage("de.civitascore.portal.modelregistry..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("de.civitascore.modelforge..")
          .because(
              "Model Forge is integrated behind the ModelRegistryGateway anti-corruption layer;"
                  + " no other package may touch its classes directly");
}
