package de.civitascore.portal.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

/**
 * Makes the test client leave unset DTO fields out of request bodies, as the frontend does with
 * {@code undefined}. The API rejects an explicit {@code null} for some fields, so a DTO body must
 * not turn an unset field into one. A {@code null} value in a {@code Map} body is still sent.
 */
@TestConfiguration
public class OmitNullRequestFieldsConfiguration {

  @Bean
  RestTemplateCustomizer omitNullRequestFields() {
    return restTemplate ->
        restTemplate
            .getMessageConverters()
            .replaceAll(
                converter ->
                    converter instanceof JacksonJsonHttpMessageConverter jackson
                        ? new JacksonJsonHttpMessageConverter(
                            jackson
                                .getMapper()
                                .rebuild()
                                .changeDefaultPropertyInclusion(
                                    inclusion ->
                                        inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                                .build())
                        : converter);
  }
}
