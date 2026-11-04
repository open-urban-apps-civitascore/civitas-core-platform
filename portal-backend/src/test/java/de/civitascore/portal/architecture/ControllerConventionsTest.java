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
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitReference;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSetOwnedInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionMetaInputDTO;
import de.civitascore.portal.service.DataSetOwnedService;
import de.civitascore.portal.service.DataStructureVersionService;
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

  private static final String SERVICE_PACKAGE = "de.civitascore.portal.service";
  private static final String CONTROLLER_PACKAGE = "de.civitascore.portal.controller..";

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
  static final ArchRule controllers_doNotBypassTheParentCheckOfDataSetOwnedEntities =
      noClasses()
          .that()
          .resideInAPackage(CONTROLLER_PACKAGE)
          .should()
          .callMethodWhere(
              target(owner(assignableTo(DataSetOwnedService.class)))
                  .and(not(target(dataSetCheckedOperation()))))
          .because(
              "OPA authorizes a nested route against the dataset in its path only, and only the"
                  + " operations that take the dataset id reject an entity of another dataset");

  @ArchTest
  static final ArchRule controllers_doNotBypassTheParentCheckOfDataStructureVersions =
      noClasses()
          .that()
          .resideInAPackage(CONTROLLER_PACKAGE)
          .should()
          .callMethodWhere(
              target(owner(assignableTo(DataStructureVersionService.class)))
                  .and(not(target(dataStructureCheckedOperation()))))
          .because(
              "OPA authorizes a nested route against the data structure in its path only, and only"
                  + " the operations that take the data structure id reject a version of another"
                  + " data structure");

  @ArchTest
  static final ArchRule controllers_referToNoServiceOperationByMethodReference =
      noClasses()
          .that()
          .resideInAPackage(CONTROLLER_PACKAGE)
          .should()
          .accessTargetWhere(methodReferenceIntoServices())
          .because(
              "a method reference names the class that declares the operation, not the service it"
                  + " is called on, so the parent-check rules cannot see it");

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

  private static DescribedPredicate<JavaAccess<?>> methodReferenceIntoServices() {
    return DescribedPredicate.describe(
        "a method reference to a service operation",
        access ->
            access instanceof JavaCodeUnitReference<?>
                && access.getTargetOwner().getPackageName().startsWith(SERVICE_PACKAGE));
  }

  private static DescribedPredicate<CodeUnitCallTarget> dataSetCheckedOperation() {
    return oneOf(
        "findOwned(dataSetId, id), findAll(dataSetId, spec, pageable), create(dataSetId, input),"
            + " update(dataSetId, id, input) or delete(dataSetId, id[, confirmDataLoss])",
        signature("findOwned", UUID.class, UUID.class),
        signature("findAll", UUID.class, Specification.class, Pageable.class),
        signature("create", UUID.class, DataSetOwnedInputDTO.class),
        signature("update", UUID.class, UUID.class, DataSetOwnedInputDTO.class),
        signature("delete", UUID.class, UUID.class),
        signature("delete", UUID.class, UUID.class, boolean.class));
  }

  private static DescribedPredicate<CodeUnitCallTarget> dataStructureCheckedOperation() {
    return oneOf(
        "findOwned, create, update, updateReleasedMeta, release, unrelease, duplicate or delete"
            + " with the data structure id, or toMetaInput(version)",
        signature("findOwned", UUID.class, UUID.class),
        signature("create", UUID.class, DataStructureVersionInputDTO.class),
        signature("update", UUID.class, UUID.class, DataStructureVersionInputDTO.class),
        signature(
            "updateReleasedMeta", UUID.class, UUID.class, DataStructureVersionMetaInputDTO.class),
        signature("release", UUID.class, UUID.class),
        signature("unrelease", UUID.class, UUID.class),
        signature("duplicate", UUID.class, UUID.class),
        signature("delete", UUID.class, UUID.class),
        signature("toMetaInput", DataStructureVersion.class));
  }

  @SafeVarargs
  private static DescribedPredicate<CodeUnitCallTarget> oneOf(
      String description, List<String>... signatures) {
    List<List<String>> allowed = List.of(signatures);
    return DescribedPredicate.describe(
        description,
        target ->
            allowed.contains(
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
