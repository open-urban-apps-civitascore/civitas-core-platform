package de.civitascore.modelforge.integrations.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "de.civitascore.modelforge.integrations",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class IntegrationsArchitectureTest {

    @ArchTest
    static final ArchRule integrations_is_web_and_transport_free =
        noClasses()
            .that().resideInAPackage("de.civitascore.modelforge.integrations..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "de.civitascore.modelforge.facade..",
                "org.springframework..",
                "jakarta.servlet..",
                "io.swagger..",
                "org.springdoc.."
            );
}
