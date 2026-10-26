package de.civitascore.portal.problem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProblemDetailsTest {

  private final ProblemDetails problemDetails = TestProblemDetails.create();

  @Test
  void example_ofFreeFormType_showsItsSampleDetailAndMembers() {
    assertThat(problemDetails.example(ProblemType.SAGA_IN_FLIGHT))
        .containsEntry("type", "urn:civitas:error:SAGA_IN_FLIGHT")
        .containsEntry("title", "Saga in progress")
        .containsEntry("status", 409)
        .containsEntry("detail", "Cannot write while a saga is in-flight: CREATE")
        .containsEntry("instance", "/v1/datasets/3fa85f64-5717-4562-b3fc-2c963f66afa6")
        .containsEntry("pendingSagaType", "CREATE");
  }

  @Test
  void example_ofFixedType_showsItsDetail() {
    assertThat(problemDetails.example(ProblemType.ACCESS_DENIED))
        .containsEntry("detail", "Insufficient privileges")
        .containsOnlyKeys("type", "title", "status", "detail", "instance");
  }
}
