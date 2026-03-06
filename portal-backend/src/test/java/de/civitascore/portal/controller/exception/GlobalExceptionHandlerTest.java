package de.civitascore.portal.controller.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.civitascore.portal.util.ForbiddenException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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

      ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("NOT_FOUND");
      assertThat((String) response.getBody().get("message")).contains(id.toString());
    }

    @Test
    @DisplayName("Should return 400 for InvalidInputException")
    void shouldReturn400ForInvalidInput() {
      InvalidInputException ex =
          new InvalidInputException("Pipeline", UUID.randomUUID(), "Name is required");

      ResponseEntity<Map<String, Object>> response = handler.handleInvalidInput(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("INVALID_INPUT");
      assertThat(response.getBody().get("message")).isEqualTo("Name is required");
    }

    @Test
    @DisplayName("Should return 409 for UniqueConstraintViolationException")
    void shouldReturn409ForUniqueConstraint() {
      UniqueConstraintViolationException ex =
          new UniqueConstraintViolationException("Pipeline", "name", "test-pipeline");

      ResponseEntity<Map<String, Object>> response = handler.handleUniqueConstraint(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("UNIQUE_CONSTRAINT_VIOLATION");
    }

    @Test
    @DisplayName("Should return 409 for ResourceInUseException")
    void shouldReturn409ForResourceInUse() {
      UUID id = UUID.randomUUID();
      ResourceInUseException ex =
          new ResourceInUseException("DataStructureVersion", id, "Referenced by DataSource");

      ResponseEntity<Map<String, Object>> response = handler.handleResourceInUse(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("RESOURCE_IN_USE");
      assertThat(response.getBody().get("message")).isEqualTo("Referenced by DataSource");
    }

    @Test
    @DisplayName("Should return 403 for ForbiddenException")
    void shouldReturn403ForForbidden() {
      ForbiddenException ex =
          new ForbiddenException("Role", UUID.randomUUID(), "Cannot delete system role");

      ResponseEntity<Map<String, Object>> response = handler.handleForbidden(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("FORBIDDEN");
      assertThat(response.getBody().get("message")).isEqualTo("Cannot delete system role");
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

      ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("UNIQUE_CONSTRAINT_VIOLATION");
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

      ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("FOREIGN_KEY_VIOLATION");
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

      ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("NOT_NULL_VIOLATION");
    }

    @Test
    @DisplayName("Should return 500 for unknown ConstraintViolationException SQL states")
    void shouldReturn500ForUnknownSqlState() {
      ConstraintViolationException cve =
          constraintViolation("23999", "some other constraint error", "some_constraint");
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException("could not execute statement", cve);

      ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("DATA_INTEGRITY_ERROR");
    }

    @Test
    @DisplayName("Should return 500 when cause is not a ConstraintViolationException")
    void shouldReturn500ForNonConstraintCause() {
      DataIntegrityViolationException ex =
          new DataIntegrityViolationException(
              "could not execute statement", new RuntimeException("some unknown database error"));

      ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("DATA_INTEGRITY_ERROR");
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

      ResponseEntity<Map<String, Object>> response = handler.handlePersistenceException(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("ENTITY_VALIDATION_FAILED");
      assertThat(response.getBody().get("message")).isEqualTo("scopeType requires scopeId");
    }

    @Test
    @DisplayName("Should return 500 INTERNAL_SERVER_ERROR for other PersistenceException causes")
    void shouldReturn500ForOtherPersistenceCauses() {
      PersistenceException ex = new PersistenceException(new RuntimeException("unexpected error"));

      ResponseEntity<Map<String, Object>> response = handler.handlePersistenceException(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("PERSISTENCE_ERROR");
    }

    @Test
    @DisplayName("Should return 500 INTERNAL_SERVER_ERROR when cause is null")
    void shouldReturn500WhenCauseIsNull() {
      PersistenceException ex = new PersistenceException("persistence error");

      ResponseEntity<Map<String, Object>> response = handler.handlePersistenceException(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("error")).isEqualTo("PERSISTENCE_ERROR");
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
      assertThat(response.getBody()).isNotNull();

      Map<String, Object> body = (Map<String, Object>) response.getBody();
      assertThat(body.get("error")).isEqualTo("VALIDATION_FAILED");

      List<Map<String, String>> fieldErrors = (List<Map<String, String>>) body.get("fieldErrors");
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
      assertThat(response.getBody()).isNotNull();

      @SuppressWarnings("unchecked")
      Map<String, Object> body = (Map<String, Object>) response.getBody();
      assertThat(body.get("error")).isEqualTo("MALFORMED_REQUEST");
    }
  }
}
