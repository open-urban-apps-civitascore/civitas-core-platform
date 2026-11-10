package de.civitascore.portal.configuration.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.TestProblemDetails;
import de.civitascore.portal.service.connector.ConnectorValidationException;
import de.civitascore.portal.service.connector.ConnectorViolation;
import de.civitascore.portal.service.removal.RemovalNotConfirmedException;
import de.civitascore.portal.service.removal.UnconfirmedRemoval;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.ModelMemberUnreachableException;
import de.civitascore.portal.util.PipelineClosureValidationException;
import de.civitascore.portal.util.SagaInFlightException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

class ProblemExamplesTest {

  private static final Set<String> STANDARD_MEMBERS =
      Set.of("type", "title", "status", "detail", "instance");

  private final ProblemExamples problemExamples =
      new ProblemExamples(TestProblemDetails.create(), TestProblemDetails.MESSAGES);

  static Stream<ProblemException> problemsWithExtensionMembers() {
    UUID id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    return Stream.of(
        new ConnectorValidationException(
            List.of(new ConnectorViolation("connector.qos", "must be 0, 1 or 2"))),
        new ModelMemberUnreachableException(List.of("Orphan")),
        new SagaInFlightException(
            id, PendingSagaType.CREATE, "Cannot write while a saga is in-flight: CREATE"),
        new DataSourceScopeViolationException(List.of(id)),
        new PipelineClosureValidationException(List.of(id)),
        new RemovalNotConfirmedException(
            id,
            new UnconfirmedRemoval(List.of(), List.of(), List.of()),
            "This request deletes stored data of the Dataset; set confirmDataLoss=true to proceed"));
  }

  @Test
  void example_ofFreeFormType_showsItsSampleDetailAndMembers() {
    assertThat(problemExamples.example(ProblemType.SAGA_IN_FLIGHT))
        .containsEntry("type", "urn:civitas:error:SAGA_IN_FLIGHT")
        .containsEntry("title", "Saga in progress")
        .containsEntry("status", 409)
        .containsEntry("detail", "Cannot write while a saga is in-flight: CREATE")
        .containsEntry("instance", "/v1/datasets/3fa85f64-5717-4562-b3fc-2c963f66afa6")
        .containsEntry("pendingSagaType", "CREATE");
  }

  @Test
  void example_ofFixedType_showsItsDetail() {
    assertThat(problemExamples.example(ProblemType.ACCESS_DENIED))
        .containsEntry("detail", "Insufficient privileges")
        .containsOnlyKeys(STANDARD_MEMBERS);
  }

  /** Fails when an exception renames an extension member and the documented example keeps it. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("problemsWithExtensionMembers")
  void example_namesTheExtensionMembersOfItsException(ProblemException problem) {
    Set<String> exampleMembers = new HashSet<>(problemExamples.example(problem.type()).keySet());
    exampleMembers.removeAll(STANDARD_MEMBERS);

    assertThat(exampleMembers).isEqualTo(problem.properties().keySet());
  }

  @Test
  void everyExceptionWithExtensionMembers_isCheckedAgainstItsExample() {
    Set<Class<?>> checked =
        new HashSet<>(problemsWithExtensionMembers().<Class<?>>map(Object::getClass).toList());

    assertThat(exceptionsDeclaringProperties()).isEqualTo(checked);
  }

  private static Set<Class<?>> exceptionsDeclaringProperties() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(ProblemException.class));
    Set<Class<?>> declaring = new HashSet<>();
    for (BeanDefinition candidate : scanner.findCandidateComponents("de.civitascore.portal")) {
      Class<?> exception = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
      if (Arrays.stream(exception.getDeclaredMethods())
          .anyMatch(method -> "properties".equals(method.getName()))) {
        declaring.add(exception);
      }
    }
    return declaring;
  }
}
