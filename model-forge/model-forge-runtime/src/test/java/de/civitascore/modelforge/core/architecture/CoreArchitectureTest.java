package de.civitascore.modelforge.core.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "de.civitascore.modelforge",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class CoreArchitectureTest {

    /**
     * The core slice (ports + facade implementation) stays Spring- and
     * infrastructure-free: it depends only on contract and JDK/Jackson. The
     * application slice legitimately uses spring-context/tx and is guarded by
     * its own, narrower rules in {@code ApplicationArchitectureTest} (no web,
     * no servlet, no persistence adapter).
     */
    @ArchTest
    static final ArchRule core_is_web_and_infrastructure_free =
        noClasses()
            .that().resideInAnyPackage("de.civitascore.modelforge.core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "de.civitascore.modelforge.application..",
                "de.civitascore.modelforge.integrations..",
                "de.civitascore.modelforge.persistence..",
                "org.springframework..",
                "jakarta.servlet..",
                "java.sql..",
                "javax.sql..",
                "org.postgresql..",
                "org.flywaydb..",
                "io.swagger..",
                "org.springdoc.."
            );

    /**
     * Section 7.1's consumers_use_only_public_api rule: the public facade interface
     * (contract's ModelForge) may only depend on the contract and JDK/Jackson types — never on
     * ports/persistence/infrastructure directly.
     *
     * <p>Note: this currently only reaches the {@code facade} package, i.e. the {@code ModelForge}
     * interface itself (which trivially complies, being just method signatures over contract
     * types) — its real implementation, {@code application.EmbeddedModelForgeOperations}, is
     * already covered by {@code core_is_web_and_infrastructure_free} above and by
     * {@code ApplicationArchitectureTest}, not by this rule.
     */
    @ArchTest
    static final ArchRule consumers_use_only_public_api =
        classes()
            .that().resideInAnyPackage(
                "de.civitascore.modelforge.facade.."
            )
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "java..",
                "tools.jackson..",
                "com.fasterxml.jackson.annotation..",
                "de.civitascore.modelforge.contract..",
                "de.civitascore.modelforge.facade.."
            );
}
