package de.civitascore.modelforge.spring.boot;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "de.civitascore.modelforge.spring.boot",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ModelForgeStarterArchitectureTest {

    @ArchTest
    static final ArchRule starter_does_not_pull_web_or_service_api =
        noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.web..",
                "org.springframework.security..",
                "jakarta.servlet..",
                "io.swagger..",
                "org.springdoc.."
            );
}
