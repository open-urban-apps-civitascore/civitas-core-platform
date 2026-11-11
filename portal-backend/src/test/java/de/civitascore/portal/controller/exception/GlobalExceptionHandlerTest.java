package de.civitascore.portal.controller.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.problem.FieldError;
import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.TestProblemDetails;
import de.civitascore.portal.service.connector.ConnectorValidationException;
import de.civitascore.portal.service.connector.ConnectorViolation;
import de.civitascore.portal.service.connector.StoredConnectorUnreadableException;
import de.civitascore.portal.util.ConnectionTestBusyException;
import de.civitascore.portal.util.DataSetNotEditableException;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.ForbiddenException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ModelMemberUnreachableException;
import de.civitascore.portal.util.ModelMissingException;
import de.civitascore.portal.util.ModelReferenceNotAvailableException;
import de.civitascore.portal.util.ModelRootMissingException;
import de.civitascore.portal.util.PipelineClosureValidationException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.SagaInFlightException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

  private static final String TEST_URI = "/v1/datasets";

  private final GlobalExceptionHandler handler = handler();

  private static GlobalExceptionHandler handler() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler(TestProblemDetails.create());
    handler.setMessageSource(TestProblemDetails.MESSAGES);
    return handler;
  }

  static Stream<ProblemException> freeFormProblems() {
    UUID id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    return Stream.of(
        new ResourceNotFoundException("DataSet", id),
        new InvalidInputException("Layer", "layerName", "layerName must not be blank"),
        new MalformedDataSourceRequestException("Unknown key: connector.foo"),
        new DataSetNotEditableException("Dataset must be DRAFT"),
        new ModelMemberUnreachableException(List.of("Orphan")),
        new UniqueConstraintViolationException("Layer", "layerName", "it's taken"),
        new ResourceInUseException("Style", id, "Style is still referenced by a Layer"),
        new SagaInFlightException(
            id, PendingSagaType.CREATE, "Cannot write while a saga is in-flight: CREATE"),
        new DataSourceScopeViolationException(List.of(id)),
        new PipelineClosureValidationException(List.of(id)),
        new ForbiddenException("Role", id, "Cannot delete system role"),
        new ConnectionTestBusyException("A connection test of this user is still running"));
  }

  private static ConstraintViolationException constraintViolation(
      String sqlState, String message, String constraintName) {
    SQLException sqlException = new SQLException(message, sqlState);
    return new ConstraintViolationException(message, sqlException, constraintName);
  }

  @Nested
  @DisplayName("Domain exception handling")
  class DomainExceptionTests {

    /** portal-frontend matches substrings of the detail, so a changed text breaks its messages. */
    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "de.civitascore.portal.controller.exception.GlobalExceptionHandlerTest#freeFormProblems")
    void freeFormProblem_answersTheThrowSiteTextUnchanged(ProblemException ex) {
      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getDetail()).isEqualTo(ex.getMessage());
    }

    @Test
    void problemException_isAnsweredWithItsCatalogTypeTitleAndProperties() {
      ConnectorValidationException ex =
          new ConnectorValidationException(
              List.of(new ConnectorViolation("connector.qos", "must be 0, 1 or 2")));

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:VALIDATION_FAILED");
      assertThat(problemDetail.getTitle()).isEqualTo("Validation failed");
      assertThat(problemDetail.getDetail()).isEqualTo("Input validation failed");
      assertThat(problemDetail.getProperties())
          .containsEntry(
              "fieldErrors", List.of(new FieldError("connector.qos", "must be 0, 1 or 2")));
    }

    @Test
    @DisplayName("Should return 404 for ResourceNotFoundException")
    void shouldReturn404ForNotFound() {
      UUID id = UUID.randomUUID();
      ResourceNotFoundException ex = new ResourceNotFoundException("DataSet", id);

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:NOT_FOUND");
      assertThat(problemDetail.getDetail()).contains(id.toString());
    }

    @Test
    @DisplayName("Should return 400 for InvalidInputException")
    void shouldReturn400ForInvalidInput() {
      InvalidInputException ex =
          new InvalidInputException("Pipeline", UUID.randomUUID(), "Name is required");

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:INVALID_INPUT");
      assertThat(problemDetail.getDetail()).isEqualTo("Name is required");
    }

    @Test
    @DisplayName("Should return 400 for DataSetNotEditableException")
    void shouldReturn400ForDataSetNotEditable() {
      DataSetNotEditableException ex = new DataSetNotEditableException("Dataset must be DRAFT");

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:DATASET_NOT_EDITABLE");
      assertThat(problemDetail.getDetail()).isEqualTo("Dataset must be DRAFT");
    }

    @Test
    @DisplayName("Should return 400 with its own type for ModelRootMissingException")
    void shouldReturn400ForModelRootMissing() {
      ModelRootMissingException ex = new ModelRootMissingException();

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:MODEL_ROOT_MISSING");
      assertThat(problemDetail.getDetail())
          .isEqualTo(
              "DataStructureVersion model must designate exactly one root Element before"
                  + " releasing");
    }

    @Test
    @DisplayName("Should return 400 with its own type for ModelReferenceNotAvailableException")
    void shouldReturn400ForModelReferenceNotAvailable() {
      ModelReferenceNotAvailableException ex = new ModelReferenceNotAvailableException();

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType())
          .hasToString("urn:civitas:error:MODEL_REFERENCE_NOT_AVAILABLE");
      assertThat(problemDetail.getDetail())
          .isEqualTo(
              "A referenced model is not available: it must exist, be readable, and be referenced"
                  + " with a version");
    }

    @Test
    @DisplayName(
        "Should return 400 naming the unreachable members for ModelMemberUnreachableException")
    void shouldReturn400ForModelMemberUnreachable() {
      ModelMemberUnreachableException ex = new ModelMemberUnreachableException(List.of("Orphan"));

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:MODEL_MEMBER_UNREACHABLE");
      assertThat(problemDetail.getProperties()).containsEntry("memberNames", List.of("Orphan"));
    }

    @Test
    @DisplayName("Should return 409 with its own type for ModelMissingException")
    void shouldReturn409ForModelMissing() {
      ModelMissingException ex = new ModelMissingException();

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:MODEL_MISSING");
      assertThat(problemDetail.getDetail())
          .isEqualTo("DataStructureVersion model is missing from the model registry");
    }

    @Test
    @DisplayName("Should return 409 for UniqueConstraintViolationException")
    void shouldReturn409ForUniqueConstraint() {
      UniqueConstraintViolationException ex =
          new UniqueConstraintViolationException("Pipeline", "name", "test-pipeline");

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType())
          .hasToString("urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION");
    }

    @Test
    @DisplayName("Should return 409 for ResourceInUseException")
    void shouldReturn409ForResourceInUse() {
      UUID id = UUID.randomUUID();
      ResourceInUseException ex =
          new ResourceInUseException("DataStructureVersion", id, "Referenced by DataSource");

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:RESOURCE_IN_USE");
      assertThat(problemDetail.getDetail()).isEqualTo("Referenced by DataSource");
      assertThat(problemDetail.getProperties()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Should keep the blocking artifacts out of the response body")
    void shouldNotReturnBlockersForResourceInUse() {
      List<String> blockers =
          List.of(
              "urn:core:platform:civitas:datasink:common:00000000-0000-4000-8000-000000000031",
              "urn:core:platform:civitas:mapping:common:00000000-0000-4000-8000-000000000032");
      ResourceInUseException ex =
          new ResourceInUseException(
              "DataStructure", UUID.randomUUID(), "Still referenced", blockers);

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getDetail()).isEqualTo("Still referenced");
      assertThat(problemDetail.getProperties()).isNullOrEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PendingSagaType.class)
    @DisplayName("Should return 409 with the pending saga type for SagaInFlightException")
    void shouldReturn409ForSagaInFlight(PendingSagaType pendingSagaType) {
      UUID id = UUID.randomUUID();
      SagaInFlightException ex =
          new SagaInFlightException(
              id, pendingSagaType, "Cannot write while a saga is in-flight: " + pendingSagaType);

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:SAGA_IN_FLIGHT");
      assertThat(problemDetail.getDetail()).contains(pendingSagaType.name());
      assertThat(problemDetail.getProperties()).containsEntry("pendingSagaType", pendingSagaType);
    }

    @Test
    @DisplayName("Should return 422 naming the blocked pipelines for PipelineClosureValidation")
    void shouldReturn422NamingTheBlockedPipelines() {
      UUID first = UUID.randomUUID();
      UUID second = UUID.randomUUID();

      ProblemDetail problemDetail =
          handler.handleProblem(new PipelineClosureValidationException(List.of(first, second)));

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:PIPELINE_CLOSURE_INVALID");
      assertThat(problemDetail.getProperties())
          .containsEntry("offendingPipelineIds", List.of(first, second));
      assertThat(problemDetail.getDetail())
          .as("the reply names no artifact, so the message has to state every condition")
          .contains("exist", "readable", "released")
          .doesNotContain("urn:core:");
      assertThat(problemDetail.getProperties().toString())
          .as("an artifact added as a further property would disclose it just as well")
          .doesNotContain("urn:core:");
    }

    @Test
    @DisplayName("Should return 403 for ForbiddenException")
    void shouldReturn403ForForbidden() {
      ForbiddenException ex =
          new ForbiddenException("Role", UUID.randomUUID(), "Cannot delete system role");

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:FORBIDDEN");
      assertThat(problemDetail.getDetail()).isEqualTo("Cannot delete system role");
    }

    @Test
    @DisplayName("Should return 429 for a connection test that cannot start now")
    void shouldReturn429ForBusyConnectionTest() {
      ConnectionTestBusyException ex =
          new ConnectionTestBusyException("A connection test of this user is still running");

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:TOO_MANY_REQUESTS");
      assertThat(problemDetail.getTitle()).isEqualTo("Too many requests");
      assertThat(problemDetail.getDetail())
          .isEqualTo("A connection test of this user is still running");
    }
  }

  @Nested
  @DisplayName("DataIntegrityViolationException handling")
  class DataIntegrityViolationTests {

    @Test
    @DisplayName("Should return 409 CONFLICT for unique constraint violations (SQL state 23505)")
    void shouldReturn409ForUniqueConstraint() {
      ConstraintViolationException cve =
          constraintViolation(
              "23505",
              "ERROR: duplicate key value violates unique constraint \"uk_name\" "
                  + "Detail: Key (name)=(test) already exists.",
              "uk_name");
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException("could not execute statement", cve);

      ProblemDetail problemDetail = handler.handleDataIntegrityViolation(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType())
          .hasToString("urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION");
    }

    @Test
    @DisplayName("Should return 409 CONFLICT for foreign key violations (SQL state 23503)")
    void shouldReturn409ForForeignKeyViolation() {
      ConstraintViolationException cve =
          constraintViolation(
              "23503",
              "ERROR: insert or update on table \"pipeline\" violates foreign key constraint",
              "fk_pipeline_dataset");
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException("could not execute statement", cve);

      ProblemDetail problemDetail = handler.handleDataIntegrityViolation(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:FOREIGN_KEY_VIOLATION");
    }

    @Test
    @DisplayName("Should return 400 BAD_REQUEST for not-null violations (SQL state 23502)")
    void shouldReturn400ForNotNullViolation() {
      ConstraintViolationException cve =
          constraintViolation(
              "23502",
              "ERROR: null value in column \"name\" violates not-null constraint",
              "name_not_null");
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException("could not execute statement", cve);

      ProblemDetail problemDetail = handler.handleDataIntegrityViolation(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:NOT_NULL_VIOLATION");
    }

    @Test
    @DisplayName("Should return 500 for unknown ConstraintViolationException SQL states")
    void shouldReturn500ForUnknownSqlState() {
      ConstraintViolationException cve =
          constraintViolation("23999", "some other constraint error", "some_constraint");
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException("could not execute statement", cve);

      ProblemDetail problemDetail = handler.handleDataIntegrityViolation(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:DATA_INTEGRITY_ERROR");
    }

    @Test
    @DisplayName("Should return 500 when cause is not a ConstraintViolationException")
    void shouldReturn500ForNonConstraintCause() {
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException(
              "could not execute statement", new RuntimeException("some unknown database error"));

      ProblemDetail problemDetail = handler.handleDataIntegrityViolation(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:DATA_INTEGRITY_ERROR");
    }
  }

  @Nested
  @DisplayName("PersistenceException handling")
  class PersistenceExceptionTests {

    @Test
    @DisplayName("Should return 400 BAD_REQUEST for IllegalStateException from @PrePersist")
    void shouldReturn400ForPrePersistIllegalState() {
      PersistenceException ex =
          new PersistenceException(new IllegalStateException("scopeType requires scopeId"));

      ProblemDetail problemDetail = handler.handlePersistenceException(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:ENTITY_VALIDATION_FAILED");
      assertThat(problemDetail.getDetail()).isEqualTo("Entity validation failed");
    }

    @Test
    @DisplayName("Should return 500 INTERNAL_SERVER_ERROR for other PersistenceException causes")
    void shouldReturn500ForOtherPersistenceCauses() {
      PersistenceException ex = new PersistenceException(new RuntimeException("unexpected error"));

      ProblemDetail problemDetail = handler.handlePersistenceException(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:PERSISTENCE_ERROR");
    }

    @Test
    @DisplayName("Should return 500 INTERNAL_SERVER_ERROR when cause is null")
    void shouldReturn500WhenCauseIsNull() {
      PersistenceException ex = new PersistenceException("persistence error");

      ProblemDetail problemDetail = handler.handlePersistenceException(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:PERSISTENCE_ERROR");
    }

    @Test
    void storedConnectorUnreadable_returns500WithoutTheUrn() {
      String urn = "urn:core:platform:civitas:datasource:common:" + UUID.randomUUID() + ":1.0.0";
      StoredConnectorUnreadableException ex =
          new StoredConnectorUnreadableException(UUID.randomUUID(), urn);

      ProblemDetail problemDetail = handler.handleProblem(ex);

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:PERSISTENCE_ERROR");
      assertThat(problemDetail.getDetail()).doesNotContain(urn);
    }
  }

  @Nested
  @DisplayName("Spring MVC exception handling")
  class SpringMvcExceptionTests {

    @Test
    @DisplayName("Should return 400 with field errors for MethodArgumentNotValidException")
    @SuppressWarnings("unchecked")
    void shouldReturn400WithFieldErrors() {
      MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "input");
      bindingResult.rejectValue("name", "NotBlank", "Name is required");
      bindingResult.rejectValue("description", "NotBlank", "Description must not be blank");

      MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

      ResponseEntity<Object> response =
          handler.handleMethodArgumentNotValid(
              ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);

      ProblemDetail problemDetail = (ProblemDetail) response.getBody();
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:VALIDATION_FAILED");

      List<FieldError> fieldErrors =
          (List<FieldError>) problemDetail.getProperties().get("fieldErrors");
      assertThat(fieldErrors)
          .containsExactlyInAnyOrder(
              new FieldError("name", "Name is required"),
              new FieldError("description", "Description must not be blank"));
    }

    @Test
    @DisplayName("Should return 400 for HttpMessageNotReadableException")
    void shouldReturn400ForMalformedJson() {
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException(
              "JSON parse error", new MockHttpInputMessage(new byte[0]));

      ResponseEntity<Object> response =
          handler.handleHttpMessageNotReadable(
              ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);

      ProblemDetail problemDetail = (ProblemDetail) response.getBody();
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:MALFORMED_REQUEST");
      assertThat(problemDetail.getTitle()).isEqualTo("Malformed request");
      assertThat(problemDetail.getDetail()).isEqualTo("Request body is missing or malformed");
    }
  }

  @Nested
  @DisplayName("Framework and unexpected exception handling")
  class FrameworkExceptionTests {

    private final ServletWebRequest webRequest =
        new ServletWebRequest(
            new MockHttpServletRequest("PATCH", TEST_URI), new MockHttpServletResponse());

    private ProblemDetail problemOf(ResponseEntity<Object> response) {
      assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
      return (ProblemDetail) response.getBody();
    }

    @Test
    void handleExceptionInternal_methodNotSupported_keepsAllowAndSetsMethodNotAllowedUrn()
        throws Exception {
      ResponseEntity<Object> response =
          handler.handleException(
              new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "DELETE")),
              webRequest);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
      assertThat(response.getHeaders().getAllow())
          .contains(HttpMethod.GET, HttpMethod.DELETE)
          .doesNotContain(HttpMethod.PATCH);
      ProblemDetail problemDetail = problemOf(response);
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:METHOD_NOT_ALLOWED");
      assertThat(problemDetail.getTitle()).isEqualTo("Method not allowed");
    }

    @Test
    void handleExceptionInternal_typeMismatch_namesParameterWithoutEchoingValue() throws Exception {
      TypeMismatchException ex = new TypeMismatchException("not-a-uuid", UUID.class);
      ex.initPropertyName("dataSetId");

      ResponseEntity<Object> response = handler.handleException(ex, webRequest);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      ProblemDetail problemDetail = problemOf(response);
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:INVALID_INPUT");
      assertThat(problemDetail.getDetail()).isEqualTo("Invalid value: dataSetId");
    }

    @Test
    void handleExceptionInternal_noResource_setsNotFoundUrn() throws Exception {
      ResponseEntity<Object> response =
          handler.handleException(
              new NoResourceFoundException(HttpMethod.GET, TEST_URI, "datasets"), webRequest);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(problemOf(response).getType()).hasToString("urn:civitas:error:NOT_FOUND");
    }

    @Test
    void handleExceptionInternal_mediaTypeNotSupported_setsUnsupportedMediaTypeUrn()
        throws Exception {
      ResponseEntity<Object> response =
          handler.handleException(
              new HttpMediaTypeNotSupportedException(
                  MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
              webRequest);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
      assertThat(problemOf(response).getType())
          .hasToString("urn:civitas:error:UNSUPPORTED_MEDIA_TYPE");
    }
  }
}
