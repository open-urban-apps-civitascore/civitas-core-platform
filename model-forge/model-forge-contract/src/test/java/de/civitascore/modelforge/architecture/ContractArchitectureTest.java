package de.civitascore.modelforge.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "de.civitascore.modelforge",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ContractArchitectureTest {

    @ArchTest
    static final ArchRule contract_is_transport_and_infrastructure_free =
        noClasses()
            .that().resideInAPackage("de.civitascore.modelforge..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.servlet..",
                "java.sql..",
                "javax.sql..",
                "org.postgresql..",
                "org.flywaydb..",
                "io.swagger..",
                "org.springdoc.."
            );
}
