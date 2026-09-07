package de.civitascore.portal.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Keeps the Model Forge administration console and the schema-import operations out of the portal.
 *
 * <p>The console is an unauthenticated read-write UI over the whole registry, and the import
 * operations reach external catalogues. Both are development-only surfaces whose protection is that
 * the portal cannot reach them. That holds today, but nothing keeps it holding: a dependency added
 * in a year would restore the console silently.
 *
 * <p>The console cannot be guarded by an ArchUnit rule. {@code ModelForgeBoundaryTest} inspects
 * classes under {@code de.civitascore.portal} and fires on a bytecode reference, so adding the
 * console as a dependency — which puts its pages on the classpath with no code referencing them —
 * leaves every such rule green. Its absence is therefore asserted against the classpath itself.
 *
 * <p>Of the two checks below, {@link #wicketIsNotOnTheClasspath} is the one that detects the
 * console arriving as a dependency: the console is packaged as an executable archive, so its own
 * classes sit under {@code BOOT-INF/classes} and stay invisible to {@link Class#forName} even once
 * it is on the dependency list, while the Wicket it needs resolves normally and becomes visible.
 * Both were confirmed by adding the dependency and observing which checks fail.
 */
@AnalyzeClasses(packages = "de.civitascore.portal", importOptions = DoNotIncludeTests.class)
class ModelForgeProductionBoundaryTest {

  /**
   * Every page of the console, plus both of its application entry points. Named individually rather
   * than by package so that adding a page does not quietly escape the check. This catches the
   * console arriving as a plain library or its classes being copied in; for the packaged console
   * see {@link #wicketIsNotOnTheClasspath}.
   */
  @ParameterizedTest(name = "{0} is not on the portal classpath")
  @ValueSource(
      strings = {
        "de.civitascore.modelforge.adminui.AdminUiApplication",
        "de.civitascore.modelforge.adminui.wicket.AdminWicketApplication",
        "de.civitascore.modelforge.adminui.wicket.BasePage",
        "de.civitascore.modelforge.adminui.wicket.pages.ArtifactEditPage",
        "de.civitascore.modelforge.adminui.wicket.pages.ArtifactListPage",
        "de.civitascore.modelforge.adminui.wicket.pages.ArtifactViewPage",
        "de.civitascore.modelforge.adminui.wicket.pages.GraphPage",
        "de.civitascore.modelforge.adminui.wicket.pages.ImportPage",
        "de.civitascore.modelforge.adminui.wicket.pages.SchemaViewComparisonPage",
        "de.civitascore.modelforge.adminui.wicket.pages.SeedPage",
        "de.civitascore.modelforge.adminui.wicket.pages.SmartDataModelImportPage",
        "de.civitascore.modelforge.adminui.wicket.pages.ValidatePage",
        "de.civitascore.modelforge.adminui.wicket.pages.XRepositoryImportPage",
      })
  @DisplayName("No administration-console class is reachable from the portal")
  void administrationConsoleIsNotOnTheClasspath(String className) {
    assertThatThrownBy(() -> Class.forName(className))
        .as(
            "%s must not be reachable: the console has no authentication of its own and relies on"
                + " being unreachable from the portal",
            className)
        .isInstanceOf(ClassNotFoundException.class);
  }

  @ParameterizedTest(name = "{0} is not on the portal classpath")
  @ValueSource(
      strings = {
        "org.apache.wicket.protocol.http.WicketFilter",
        "org.apache.wicket.protocol.http.WebApplication",
      })
  @DisplayName("The console's web framework is not on the portal classpath either")
  void wicketIsNotOnTheClasspath(String className) {
    // Catches the console arriving transitively: its pages would need Wicket, so Wicket appearing
    // is the earlier and broader signal.
    assertThatThrownBy(() -> Class.forName(className)).isInstanceOf(ClassNotFoundException.class);
  }

  /**
   * The portal must reach no external catalogue.
   *
   * <p>Scoped to the operations that fetch from outside: {@code importFromXRepository}, {@code
   * importFromSmartDataModels} and {@code searchXRepository}. {@code importSchema} is deliberately
   * not included — it accepts a document the caller already holds ({@code
   * ImportSchemaCommand(JsonNode, …)}, no URL) and is how the portal stores a model an author drew.
   */
  @ArchTest
  static final ArchRule portal_reaches_no_external_catalogue =
      ArchRuleDefinition.noClasses()
          .should()
          .callMethodWhere(
              target(owner(assignableTo(ModelForge.class)))
                  .and(target(nameMatching("importFrom.*|searchXRepository"))))
          .because(
              "importing from XRepository or Smart Data Models is a development-only capability of"
                  + " the administration console and adds no portal API");

  @Test
  @DisplayName("The registry gateway offers no operation that fetches from an external catalogue")
  void gatewayOffersNoExternalCatalogueOperation() {
    // The gateway is the portal's only door to Model Forge, so such an operation would have to
    // surface here first.
    List<String> externalOperations =
        Arrays.stream(ModelRegistryGateway.class.getDeclaredMethods())
            .map(Method::getName)
            .filter(name -> name.contains("XRepository") || name.contains("SmartDataModel"))
            .toList();

    assertThat(externalOperations)
        .as("the anti-corruption layer must not expose an external-catalogue operation")
        .isEmpty();
  }
}
