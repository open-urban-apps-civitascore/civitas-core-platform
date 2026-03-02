package de.civitascore.portal.controller.exception;

import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class DataIntegrityExceptionHandler {

  private static final Pattern UNIQUE_DETAIL_PATTERN =
      Pattern.compile("Key \\((.+?)\\)=\\((.+?)\\) already exists");

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
    String message = ex.getMostSpecificCause().getMessage();
    if (message != null && message.contains("unique constraint")) {
      UniqueConstraintViolationException mapped = extractUniqueViolationException(message);
      log.warn("Unique constraint violation: {}", mapped.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
    log.error("Data integrity violation: {}", message);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
  }

  private UniqueConstraintViolationException extractUniqueViolationException(String dbMessage) {
    Matcher matcher = UNIQUE_DETAIL_PATTERN.matcher(dbMessage);
    if (matcher.find()) {
      return new UniqueConstraintViolationException(matcher.group(1), matcher.group(2));
    }
    return new UniqueConstraintViolationException();
  }
}
