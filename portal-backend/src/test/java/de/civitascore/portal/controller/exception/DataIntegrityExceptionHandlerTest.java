package de.civitascore.portal.controller.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("DataIntegrityExceptionHandler Unit Tests")
class DataIntegrityExceptionHandlerTest {

  private final DataIntegrityExceptionHandler handler = new DataIntegrityExceptionHandler();

  private static ConstraintViolationException constraintViolation(
      String sqlState, String message, String constraintName) {
    SQLException sqlException = new SQLException(message, sqlState);
    return new ConstraintViolationException(message, sqlException, constraintName);
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
}
