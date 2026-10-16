/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.PrimaryNodeOnly;
import org.apache.nifi.annotation.behavior.TriggerSerially;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.FlowFileAccessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.web.client.provider.api.WebClientServiceProvider;

/**
 * The source of an HTTP/REST Data source: one request of one API call in each run.
 *
 * <p>It runs on the primary node alone, because each node of a cluster would call the API
 * otherwise, and one run at a time, so that two requests of one API call never overlap.
 */
@Tags({"HTTP", "REST", "API", "GET", "source", "civitas"})
@CapabilityDescription(
    "Sends the HTTP GET request of one API call of an HTTP/REST Data source. A 2xx response with a"
        + " body becomes a FlowFile on success. An error becomes a FlowFile on failure that says"
        + " whether it is fatal or transient.")
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@PrimaryNodeOnly
@TriggerSerially
@WritesAttributes({
  @WritesAttribute(attribute = "mime.type", description = "application/json, on success."),
  @WritesAttribute(
      attribute = GetHttpApiCall.STATUS_CODE,
      description = "The status of the response, on success."),
  @WritesAttribute(
      attribute = GetHttpApiCall.REQUEST_PATH,
      description = "The path of the API call, without the query."),
  @WritesAttribute(
      attribute = GetHttpApiCall.ERROR_KIND,
      description =
          "fatal or transient, on failure. A fatal error needs a change to the configuration."),
  @WritesAttribute(
      attribute = GetHttpApiCall.ERROR_STATUS,
      description = "The status of the response, or 0 when no response came, on failure."),
  @WritesAttribute(
      attribute = GetHttpApiCall.ERROR_MESSAGE,
      description = "The method, the URL without the query, and the status or the exception class.")
})
public class GetHttpApiCall extends AbstractProcessor {

  public static final String STATUS_CODE = "http.status.code";
  public static final String REQUEST_PATH = "http.request.path";
  public static final String ERROR_KIND = "http.error.kind";
  public static final String ERROR_STATUS = "http.error.status";
  public static final String ERROR_MESSAGE = "http.error.message";

  /** The explanations name no value: a user info in the URL would be a credential. */
  private static final Validator BASE_URL_VALIDATOR =
      (subject, input, context) -> result(subject, baseUrlViolation(input));

  private static final Validator USERNAME_VALIDATOR =
      (subject, input, context) -> result(subject, usernameViolation(input));

  static final PropertyDescriptor WEB_CLIENT_SERVICE =
      new PropertyDescriptor.Builder()
          .name("Web Client Service Provider")
          .displayName("Web Client Service Provider")
          .description(
              "The HTTP client of the request. Its properties carry the TLS material, the proxy,"
                  + " the timeouts and the redirect handling. A redirect it does not follow is a"
                  + " fatal error.")
          .required(true)
          .identifiesControllerService(WebClientServiceProvider.class)
          .build();

  static final PropertyDescriptor BASE_URL =
      new PropertyDescriptor.Builder()
          .name("Base URL")
          .displayName("Base URL")
          .description(
              "Scheme http or https, a host, an optional port and an optional path. No user info,"
                  + " no query, no fragment.")
          .required(true)
          .addValidator(BASE_URL_VALIDATOR)
          .build();

  static final PropertyDescriptor API_CALL =
      new PropertyDescriptor.Builder()
          .name("API Call")
          .displayName("API Call")
          .description(
              "The API call as JSON: name, method, path, queryParameters, headers and pagination.")
          .required(true)
          .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
          .build();

  static final PropertyDescriptor AUTHENTICATION_TYPE =
      new PropertyDescriptor.Builder()
          .name("Authentication Type")
          .displayName("Authentication Type")
          .description("How the request authenticates.")
          .required(true)
          .allowableValues(AuthenticationType.NONE.name(), AuthenticationType.BASIC.name())
          .defaultValue(AuthenticationType.NONE.name())
          .build();

  static final PropertyDescriptor BASIC_AUTH_USERNAME =
      new PropertyDescriptor.Builder()
          .name("Basic Auth Username")
          .displayName("Basic Auth Username")
          .description("The user the API knows. It contains no colon.")
          .required(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.BASIC.name())
          .addValidator(USERNAME_VALIDATOR)
          .build();

  static final PropertyDescriptor BASIC_AUTH_PASSWORD =
      new PropertyDescriptor.Builder()
          .name("Basic Auth Password")
          .displayName("Basic Auth Password")
          .description("The password of that user.")
          .required(true)
          .sensitive(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.BASIC.name())
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  static final Relationship SUCCESS =
      new Relationship.Builder()
          .name("success")
          .description("One FlowFile for each 2xx response with a body. The content is the body.")
          .build();

  static final Relationship FAILURE =
      new Relationship.Builder()
          .name("failure")
          .description(
              "One FlowFile for each failed run, with empty content. The error attributes say"
                  + " whether the error is fatal or transient.")
          .build();

  private static final List<PropertyDescriptor> PROPERTIES =
      List.of(
          WEB_CLIENT_SERVICE,
          BASE_URL,
          API_CALL,
          AUTHENTICATION_TYPE,
          BASIC_AUTH_USERNAME,
          BASIC_AUTH_PASSWORD);

  private static final Set<Relationship> RELATIONSHIPS = Set.of(SUCCESS, FAILURE);

  private static final String JSON = "application/json";

  private volatile ApiCall apiCall;

  @Override
  protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    return PROPERTIES;
  }

  @Override
  public Set<Relationship> getRelationships() {
    return RELATIONSHIPS;
  }

  @Override
  protected Collection<ValidationResult> customValidate(ValidationContext context) {
    List<ValidationResult> results = new ArrayList<>();
    try {
      ApiCallParser.parse(context.getProperty(API_CALL).getValue());
    } catch (InvalidApiCallException e) {
      for (String violation : e.violations()) {
        results.add(
            new ValidationResult.Builder()
                .subject(API_CALL.getDisplayName())
                .valid(false)
                .explanation(violation)
                .build());
      }
    }
    return results;
  }

  @OnScheduled
  public void readApiCall(ProcessContext context) throws InvalidApiCallException {
    apiCall = ApiCallParser.parse(context.getProperty(API_CALL).getValue());
  }

  @Override
  public void onTrigger(ProcessContext context, ProcessSession session) {
    ApiCallClient client =
        new ApiCallClient(
            context
                .getProperty(WEB_CLIENT_SERVICE)
                .asControllerService(WebClientServiceProvider.class)
                .getWebClientService(),
            context.getProperty(BASE_URL).getValue());
    Authentication authentication =
        AuthenticationType.valueOf(context.getProperty(AUTHENTICATION_TYPE).getValue())
            .authentication(context);

    switch (client.send(apiCall, authentication)) {
      case ApiCallOutcome.Body body -> {
        try (body) {
          transferBody(session, client, body);
        }
      }
      case ApiCallOutcome.NoBody noBody ->
          getLogger()
              .debug("{} returned {} without a body", client.describe(apiCall), noBody.status());
      case ApiCallOutcome.Failure failure -> transferFailure(session, failure);
    }
  }

  private void transferBody(
      ProcessSession session, ApiCallClient client, ApiCallOutcome.Body body) {
    FlowFile flowFile = session.create();
    try {
      flowFile = session.importFrom(body.content(), flowFile);
    } catch (FlowFileAccessException e) {
      session.remove(flowFile);
      transferFailure(session, ApiCallOutcome.Failure.ofTransport(client.describe(apiCall), e));
      return;
    }
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(CoreAttributes.MIME_TYPE.key(), JSON);
    attributes.put(STATUS_CODE, Integer.toString(body.status()));
    attributes.put(REQUEST_PATH, apiCall.path());
    session.transfer(session.putAllAttributes(flowFile, attributes), SUCCESS);
  }

  /** Logs the error, which raises the bulletin, and sends an empty FlowFile that describes it. */
  private void transferFailure(ProcessSession session, ApiCallOutcome.Failure failure) {
    if (failure.kind() == ErrorKind.FATAL) {
      getLogger().error(failure.message());
    } else {
      getLogger().warn(failure.message());
    }
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(REQUEST_PATH, apiCall.path());
    attributes.put(ERROR_KIND, failure.kind().attributeValue());
    attributes.put(ERROR_STATUS, Integer.toString(failure.status()));
    attributes.put(ERROR_MESSAGE, failure.message());
    session.transfer(session.putAllAttributes(session.create(), attributes), FAILURE);
  }

  private static String baseUrlViolation(String input) {
    URI uri;
    try {
      uri = new URI(input);
    } catch (URISyntaxException e) {
      return "is not a URL";
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      return "must use the scheme http or https";
    }
    if (uri.getRawUserInfo() != null) {
      return "must not contain user info";
    }
    if (uri.getHost() == null) {
      return "must name a host";
    }
    if (uri.getRawQuery() != null) {
      return "must not contain a query";
    }
    if (uri.getRawFragment() != null) {
      return "must not contain a fragment";
    }
    return null;
  }

  private static String usernameViolation(String input) {
    if (input == null || input.isEmpty()) {
      return "must not be empty";
    }
    if (input.indexOf(':') >= 0) {
      return "must not contain a colon";
    }
    return null;
  }

  private static ValidationResult result(String subject, String violation) {
    return new ValidationResult.Builder()
        .subject(subject)
        .valid(violation == null)
        .explanation(violation)
        .build();
  }
}
