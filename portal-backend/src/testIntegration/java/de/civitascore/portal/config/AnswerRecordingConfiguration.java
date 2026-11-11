package de.civitascore.portal.config;

import de.civitascore.portal.util.AnswerRecorder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Records the answers of the integration tests, see {@link AnswerRecorder}. */
@TestConfiguration
public class AnswerRecordingConfiguration {

  /** Ahead of Spring Security, so that its 401 and 403 answers are recorded too. */
  @Bean
  FilterRegistrationBean<AnswerRecorder> answerRecorder() {
    FilterRegistrationBean<AnswerRecorder> registration =
        new FilterRegistrationBean<>(new AnswerRecorder());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
