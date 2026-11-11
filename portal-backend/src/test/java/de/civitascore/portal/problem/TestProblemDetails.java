package de.civitascore.portal.problem;

import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/** The message bundle and {@link ProblemDetails} for tests that build handlers without Spring. */
public final class TestProblemDetails {

  public static final MessageSource MESSAGES = messages();

  private TestProblemDetails() {}

  public static ProblemDetails create() {
    return new ProblemDetails(MESSAGES);
  }

  private static MessageSource messages() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
    return messageSource;
  }
}
