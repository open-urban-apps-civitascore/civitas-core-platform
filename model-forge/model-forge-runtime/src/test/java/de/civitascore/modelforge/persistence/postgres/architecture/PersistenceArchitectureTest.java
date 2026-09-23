package de.civitascore.modelforge.persistence.postgres.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "de.civitascore.modelforge.persistence.postgres",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class PersistenceArchitectureTest {

    @ArchTest
    static final ArchRule persistence_is_web_and_transport_free =
        noClasses()
            .that().resideInAPackage("de.civitascore.modelforge.persistence.postgres..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "de.civitascore.modelforge.facade..",
                "org.springframework.web..",
                "org.springframework.http..",
                "jakarta.servlet..",
                "io.swagger..",
                "org.springdoc.."
            );
}
