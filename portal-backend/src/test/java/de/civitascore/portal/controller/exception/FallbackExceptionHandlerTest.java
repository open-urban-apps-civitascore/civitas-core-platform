package de.civitascore.portal.controller.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class FallbackExceptionHandlerTest {

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new FailingController())
          .setControllerAdvice(new FallbackExceptionHandler(), new GlobalExceptionHandler())
          .build();

  @Test
  void unhandledException_answers500InternalErrorWithFixedDetail() throws Exception {
    mockMvc
        .perform(get("/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("urn:civitas:error:INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
  }

  // Guards against the catch-all shadowing Spring's cause matching in GlobalExceptionHandler.
  @Test
  void transactionFailureCausedByPersistenceException_answersPersistenceError() throws Exception {
    mockMvc
        .perform(get("/database-down"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("urn:civitas:error:PERSISTENCE_ERROR"));
  }

  @RestController
  static class FailingController {

    @GetMapping("/unexpected")
    void unexpected() {
      throw new IllegalStateException("connection pool of db-internal-7 exhausted");
    }

    @GetMapping("/database-down")
    void databaseDown() {
      throw new CannotCreateTransactionException(
          "Could not open JPA EntityManager for transaction",
          new JDBCConnectionException("Unable to acquire JDBC Connection", new SQLException()));
    }
  }
}
