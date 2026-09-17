package de.civitascore.portal.controller.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.util.DataSetNotEditableException;
import de.civitascore.portal.util.ForbiddenException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.PipelineClosureValidationException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.SagaInFlightException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

  private static final String TEST_URI = "/v1/datasets";

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  private static HttpServletRequest mockRequest() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn(TEST_URI);
    return request;
  }

  private static ConstraintViolationException constraintViolation(
      String sqlState, String message, String constraintName) {
    SQLException sqlException = new SQLException(message, sqlState);
    return new ConstraintViolationException(message, sqlException, constraintName);
  }

  @Nested
  @DisplayName("Domain exception handling")
  class DomainExceptionTests {

    @Test
    @DisplayName("Should return 404 for ResourceNotFoundException")
    void shouldReturn404ForNotFound() {
      UUID id = UUID.randomUUID();
      ResourceNotFoundException ex = new ResourceNotFoundException("DataSet", id);

      ProblemDetail problemDetail = handler.handleNotFound(ex, mockRequest());

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:NOT_FOUND");
      assertThat(problemDetail.getDetail()).contains(id.toString());
      assertThat(problemDetail.getInstance()).isEqualTo(URI.create(TEST_URI));
    }

    @Test
    @DisplayName("Should return 400 for InvalidInputException")
    void shouldReturn400ForInvalidInput() {
      InvalidInputException ex =
          new InvalidInputException("Pipeline", UUID.randomUUID(), "Name is required");

      ProblemDetail problemDetail = handler.handleInvalidInput(ex, mockRequest());

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:INVALID_INPUT");
      assertThat(problemDetail.getDetail()).isEqualTo("Name is required");
      assertThat(problemDetail.getInstance()).isEqualTo(URI.create(TEST_URI));
    }

    @Test
    @DisplayName("Should return 400 for DataSetNotEditableException")
    void shouldReturn400ForDataSetNotEditable() {
      DataSetNotEditableException ex = new DataSetNotEditableException("Dataset must be DRAFT");

      ProblemDetail problemDetail = handler.handleDataSetNotEditable(ex, mockRequest());

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:DATASET_NOT_EDITABLE");
      assertThat(problemDetail.getDetail()).isEqualTo("Dataset must be DRAFT");
      assertThat(problemDetail.getInstance()).isEqualTo(URI.create(TEST_URI));
    }

    @Test
    @DisplayName("Should return 409 for UniqueConstraintViolationException")
    void shouldReturn409ForUniqueConstraint() {
      UniqueConstraintViolationException ex =
          new UniqueConstraintViolationException("Pipeline", "name", "test-pipeline");

      ProblemDetail problemDetail = handler.handleUniqueConstraint(ex, mockRequest());

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType())
          .hasToString("urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION");
      assertThat(problemDetail.getInstance()).isEqualTo(URI.create(TEST_URI));
    }

    @Test
    @DisplayName("Should return 409 for ResourceInUseException")
    void shouldReturn409ForResourceInUse() {
      UUID id = UUID.randomUUID();
      ResourceInUseException ex =
          new ResourceInUseException("DataStructureVersion", id, "Referenced by DataSource");

      ProblemDetail problemDetail = handler.handleResourceInUse(ex, mockRequest());

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:RESOURCE_IN_USE");
      assertThat(problemDetail.getDetail()).isEqualTo("Referenced by DataSource");
      assertThat(problemDetail.getProperties()).isNullOrEmpty();
      assertThat(problemDetail.getInstance()).isEqualTo(URI.create(TEST_URI));
    }

    @Test
    @DisplayName("Should keep the blocking artifacts out of the response body")
    void shouldNotReturnBlockersForResourceInUse() {
      List<String> blockers = List.of("urn:core:datasink:Readings", "urn:core:mapping:Join");
      ResourceInUseException ex =
          new ResourceInUseException(
              "DataStructure", UUID.randomUUID(), "Still referenced", blockers);

      ProblemDetail problemDetail = handler.handleResourceInUse(ex, mockRequest());

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

      ProblemDetail problemDetail = handler.handleSagaInFlight(ex, mockRequest());

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:SAGA_IN_FLIGHT");
      assertThat(problemDetail.getDetail()).contains(pendingSagaType.name());
      assertThat(problemDetail.getProperties()).containsEntry("pendingSagaType", pendingSagaType);
      assertThat(problemDetail.getInstance()).isEqualTo(URI.create(TEST_URI));
    }

    @Test
    @DisplayName("Should return 422 naming the blocked pipelines for PipelineClosureValidation")
    void shouldReturn422NamingTheBlockedPipelines() {
      UUID first = UUID.randomUUID();
      UUID second = UUID.randomUUID();

      ProblemDetail problemDetail =
          handler.handlePipelineClosureValidation(
              new PipelineClosureValidationException(List.of(first, second)), mockRequest());

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

      ProblemDetail problemDetail = handler.handleForbidden(ex, mockRequest());

      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:FORBIDDEN");
      assertThat(problemDetail.getDetail()).isEqualTo("Cannot delete system role");
      assertThat(problemDetail.getInstance()).isEqualTo(URI.create(TEST_URI));
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

      ResponseEntity<ProblemDetail> response =
          handler.handleDataIntegrityViolation(ex, mockRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody().getType())
          .hasToString("urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION");
      assertThat(response.getBody().getInstance()).isEqualTo(URI.create(TEST_URI));
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

      ResponseEntity<ProblemDetail> response =
          handler.handleDataIntegrityViolation(ex, mockRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody().getType())
          .hasToString("urn:civitas:error:FOREIGN_KEY_VIOLATION");
      assertThat(response.getBody().getInstance()).isEqualTo(URI.create(TEST_URI));
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

      ResponseEntity<ProblemDetail> response =
          handler.handleDataIntegrityViolation(ex, mockRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:NOT_NULL_VIOLATION");
      assertThat(response.getBody().getInstance()).isEqualTo(URI.create(TEST_URI));
    }

    @Test
    @DisplayName("Should return 500 for unknown ConstraintViolationException SQL states")
    void shouldReturn500ForUnknownSqlState() {
      ConstraintViolationException cve =
          constraintViolation("23999", "some other constraint error", "some_constraint");
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException("could not execute statement", cve);

      ResponseEntity<ProblemDetail> response =
          handler.handleDataIntegrityViolation(ex, mockRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().getType())
          .hasToString("urn:civitas:error:DATA_INTEGRITY_ERROR");
    }

    @Test
    @DisplayName("Should return 500 when cause is not a ConstraintViolationException")
    void shouldReturn500ForNonConstraintCause() {
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException(
              "could not execute statement", new RuntimeException("some unknown database error"));

      ResponseEntity<ProblemDetail> response =
          handler.handleDataIntegrityViolation(ex, mockRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().getType())
          .hasToString("urn:civitas:error:DATA_INTEGRITY_ERROR");
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

      ResponseEntity<ProblemDetail> response =
          handler.handlePersistenceException(ex, mockRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getType())
          .hasToString("urn:civitas:error:ENTITY_VALIDATION_FAILED");
      assertThat(response.getBody().getDetail()).isEqualTo("Entity validation failed");
      assertThat(response.getBody().getInstance()).isEqualTo(URI.create(TEST_URI));
    }

    @Test
    @DisplayName("Should return 500 INTERNAL_SERVER_ERROR for other PersistenceException causes")
    void shouldReturn500ForOtherPersistenceCauses() {
      PersistenceException ex = new PersistenceException(new RuntimeException("unexpected error"));

      ResponseEntity<ProblemDetail> response =
          handler.handlePersistenceException(ex, mockRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:PERSISTENCE_ERROR");
    }

    @Test
    @DisplayName("Should return 500 INTERNAL_SERVER_ERROR when cause is null")
    void shouldReturn500WhenCauseIsNull() {
      PersistenceException ex = new PersistenceException("persistence error");

      ResponseEntity<ProblemDetail> response =
          handler.handlePersistenceException(ex, mockRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:PERSISTENCE_ERROR");
    }
  }

  @Nested
  @DisplayName("Spring MVC exception handling")
  class SpringMvcExceptionTests {

    @Test
    @DisplayName("Should return 400 with field errors for MethodArgumentNotValidException")
    @SuppressWarnings("unchecked")
    void shouldReturn400WithFieldErrors() {
      BeanPropertyBindingResult bindingResult =
          new BeanPropertyBindingResult(new Object(), "input");
      bindingResult.addError(new FieldError("input", "name", "Name is required"));
      bindingResult.addError(
          new FieldError("input", "description", "Description must not be blank"));

      MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

      ResponseEntity<Object> response =
          handler.handleMethodArgumentNotValid(
              ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);

      ProblemDetail problemDetail = (ProblemDetail) response.getBody();
      assertThat(problemDetail.getType()).hasToString("urn:civitas:error:VALIDATION_FAILED");

      List<Map<String, String>> fieldErrors =
          (List<Map<String, String>>) problemDetail.getProperties().get("fieldErrors");
      assertThat(fieldErrors).hasSize(2);
      assertThat(fieldErrors)
          .extracting(fe -> fe.get("field"))
          .containsExactlyInAnyOrder("name", "description");
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
    }
  }
}
