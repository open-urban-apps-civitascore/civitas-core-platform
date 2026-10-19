package de.civitascore.modelforge.application;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "de.civitascore.modelforge",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ApplicationArchitectureTest {

    @ArchTest
    static final ArchRule application_is_free_of_web_and_infrastructure_adapters =
        noClasses()
            .that().resideInAnyPackage(
                "de.civitascore.modelforge.application..",
                "de.civitascore.modelforge.graph..",
                "de.civitascore.modelforge.validation..",
                "de.civitascore.modelforge.xsd.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "de.civitascore.modelforge.persistence..",
                "de.civitascore.modelforge.spring.boot..",
                "org.springframework.http..",
                "org.springframework.web..",
                "org.springframework.security..",
                "jakarta.servlet..",
                "java.sql..",
                "javax.sql..",
                "org.postgresql..",
                "org.flywaydb..",
                "io.swagger..",
                "org.springdoc.."
            );

    @ArchTest
    static final ArchRule application_contains_no_controller_classes =
        noClasses()
            .that().resideInAnyPackage("de.civitascore.modelforge.application..")
            .should().haveSimpleNameEndingWith("Controller");

    /**
     * The application runs on Jackson 3 ({@code tools.jackson}); Jackson 2 exists solely for the
     * networknt schema validator, which consumes Jackson 2 nodes. All conversion goes through
     * {@code validation.JacksonBridge}, so Jackson 2 types must not leak beyond the validation
     * package (the annotations package is Jackson-3's own and stays exempt).
     */
    @ArchTest
    static final ArchRule jackson2_is_confined_to_the_networknt_bridge_boundary =
        noClasses()
            .that().resideOutsideOfPackage("de.civitascore.modelforge.validation..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.fasterxml.jackson.core..", "com.fasterxml.jackson.databind..");
}
