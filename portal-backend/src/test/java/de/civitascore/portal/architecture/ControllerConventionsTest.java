package de.civitascore.portal.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitCallTarget;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import de.civitascore.portal.model.input.DataSetOwnedInputDTO;
import de.civitascore.portal.service.DataSetOwnedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "de.civitascore.portal", importOptions = DoNotIncludeTests.class)
class ControllerConventionsTest {

  private static final String DECLARED_RESPONSES =
      "the 2xx answer comes from the handler signature and the error answers from @Problems";

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
          .orShould()
          .beAnnotatedWith(operationWithResponses())
          .because(DECLARED_RESPONSES);

  @ArchTest
  static final ArchRule restControllers_declareNoApiResponse =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .beAnnotatedWith(ApiResponse.class)
          .orShould()
          .beAnnotatedWith(ApiResponses.class)
          .because(DECLARED_RESPONSES);

  @ArchTest
  static final ArchRule restControllerMethods_takeTheirOperationIdFromTheMethodName =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .should()
          .beAnnotatedWith(operationWithExplicitOperationId())
          .because("springdoc names each operation after its handler method");

  @ArchTest
  static final ArchRule restControllers_doNotBypassTheParentCheckOfDataSetOwnedEntities =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .callMethodWhere(
              target(owner(assignableTo(DataSetOwnedService.class)))
                  .and(not(target(parentCheckedOperation()))))
          .because(
              "OPA authorizes a nested route against the dataset in its path only, and only the"
                  + " operations that take the dataset id, or a specification bound to it, reject"
                  + " an entity of another dataset");

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

  private static DescribedPredicate<JavaAnnotation<?>> operationWithResponses() {
    return DescribedPredicate.describe(
        "@Operation with responses",
        annotation ->
            annotation.getRawType().isEquivalentTo(Operation.class)
                && ((Object[]) annotation.get("responses").orElse(new Object[0])).length > 0);
  }

  private static DescribedPredicate<JavaAnnotation<?>> operationWithExplicitOperationId() {
    return DescribedPredicate.describe(
        "@Operation with an operationId",
        annotation ->
            annotation.getRawType().isEquivalentTo(Operation.class)
                && !((String) annotation.get("operationId").orElse("")).isEmpty());
  }

  private static DescribedPredicate<CodeUnitCallTarget> parentCheckedOperation() {
    List<List<String>> signatures =
        List.of(
            signature("findOwned", UUID.class, UUID.class),
            signature("create", UUID.class, DataSetOwnedInputDTO.class),
            signature("update", UUID.class, UUID.class, DataSetOwnedInputDTO.class),
            signature("delete", UUID.class, UUID.class),
            signature("delete", UUID.class, UUID.class, boolean.class),
            signature("findAll", Specification.class, Pageable.class));
    return DescribedPredicate.describe(
        "findOwned(dataSetId, id), create(dataSetId, input), update(dataSetId, id, input),"
            + " delete(dataSetId, id[, confirmDataLoss]) or findAll(spec, pageable)",
        target ->
            signatures.contains(
                Stream.concat(
                        Stream.of(target.getName()),
                        target.getRawParameterTypes().stream().map(JavaClass::getName))
                    .toList()));
  }

  private static List<String> signature(String name, Class<?>... parameterTypes) {
    return Stream.concat(Stream.of(name), Arrays.stream(parameterTypes).map(Class::getName))
        .toList();
  }
}
