package de.civitascore.portal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "de.civitascore.portal", importOptions = DoNotIncludeTests.class)
class ControllerConventionsTest {

  @ArchTest
  static final ArchRule restControllers_extendNoClass =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should(haveObjectAsRawSuperclass())
          .because("each controller shows all its endpoints, none inherited from a base class");

  @ArchTest
  static final ArchRule restControllerMethods_declareNoApiResponse =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .should()
          .beAnnotatedWith(ApiResponse.class)
          .orShould()
          .beAnnotatedWith(ApiResponses.class)
          .because(
              "the 2xx answer comes from the handler signature and the error answers from"
                  + " @Problems");

  @ArchTest
  static final ArchRule restControllerMethods_takeTheirOperationIdFromTheMethodName =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .should()
          .beAnnotatedWith(operationWithExplicitOperationId())
          .because("springdoc names each operation after its handler method");

  private static ArchCondition<JavaClass> haveObjectAsRawSuperclass() {
    return new ArchCondition<>("have java.lang.Object as raw superclass") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass
            .getRawSuperclass()
            .filter(superclass -> !superclass.isEquivalentTo(Object.class))
            .ifPresent(
                superclass ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            "%s extends %s".formatted(javaClass.getName(), superclass.getName()))));
      }
    };
  }

  private static DescribedPredicate<JavaAnnotation<?>> operationWithExplicitOperationId() {
    return DescribedPredicate.describe(
        "@Operation with an operationId",
        annotation ->
            annotation.getRawType().isEquivalentTo(Operation.class)
                && !((String) annotation.get("operationId").orElse("")).isEmpty());
  }
}
