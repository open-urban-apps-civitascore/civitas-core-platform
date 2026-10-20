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
import java.util.List;
import java.util.Locale;
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
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.web.client.provider.api.WebClientServiceProvider;

/**
 * The source of an HTTP/REST Data source: in each run, one request of one API call, or one request
 * for each page of an API call with pagination. A run delivers all its pages or none.
 *
 * <p>It runs on the primary node alone, because each node of a cluster would call the API
 * otherwise, and one run at a time, so that two requests of one API call never overlap.
 */
@Tags({"HTTP", "REST", "API", "GET", "source", "civitas"})
@CapabilityDescription(
    "Sends the HTTP GET request of one API call of an HTTP/REST Data source, one request for each"
        + " page when the API call has a pagination. A 2xx response with records becomes a"
        + " FlowFile on success. An error on any page discards the pages of the run before it: an"
        + " error that the next run cannot repair becomes a FlowFile on stop, any other error a"
        + " FlowFile on failure.")
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
      attribute = GetHttpApiCall.PAGE,
      description = "The page number, on success of an API call with pagination."),
  @WritesAttribute(
      attribute = GetHttpApiCall.ERROR_STATUS,
      description = "The status of the response, or 0 when no response came, on stop and failure."),
  @WritesAttribute(
      attribute = GetHttpApiCall.ERROR_MESSAGE,
      description =
          "The cause, on stop and failure. It names the API call and never the base URL, the"
              + " query, a header value, a credential or a value of the response.")
})
public class GetHttpApiCall extends AbstractProcessor {

  public static final String STATUS_CODE = "http.status.code";
  public static final String REQUEST_PATH = "http.request.path";
  public static final String PAGE = "http.page";
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

  static final PropertyDescriptor BODY_READ_TIMEOUT =
      new PropertyDescriptor.Builder()
          .name("Body Read Timeout")
          .displayName("Body Read Timeout")
          .description(
              "The longest wait for the next bytes of the response body. A longer wait is a"
                  + " transient error. The Read Timeout of the web client service ends when the"
                  + " response headers arrive.")
          .required(true)
          .defaultValue("15 secs")
          .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
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
          .allowableValues(AuthenticationType.class)
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

  static final PropertyDescriptor RECORD_SCHEMA =
      new PropertyDescriptor.Builder()
          .name("Record Schema")
          .displayName("Record Schema")
          .description(
              "The JSON Schema of the Data structure, with its imports inlined. Each record of a"
                  + " response must agree with it: each element of a JSON array, or a JSON"
                  + " object as one record. A $ref resolves only inside the schema or to a GeoJSON"
                  + " geometry of https://geojson.org/schema/, which the NAR holds. The processor"
                  + " does not start with a schema that it cannot compile.")
          .required(true)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  static final PropertyDescriptor MAX_PAGES =
      new PropertyDescriptor.Builder()
          .name("Max Pages")
          .displayName("Max Pages")
          .description(
              "The maximum number of pages in one run of an API call with pagination. The limit"
                  + " applies also when the API call has an end page. A run that reads this"
                  + " number of pages and meets no stop condition is a fatal error. Thus an API"
                  + " that ignores the page parameter and changes its response on each request"
                  + " cannot make the processor send requests without end.")
          .required(true)
          .defaultValue("1000")
          .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
          .build();

  static final Relationship SUCCESS =
      new Relationship.Builder()
          .name("success")
          .description(
              "One FlowFile for each 2xx response with a body whose records all agree with the"
                  + " Record Schema; with pagination, one for each page with records of a run"
                  + " without an error. The content is the body.")
          .build();

  static final Relationship STOP =
      new Relationship.Builder()
          .name("stop")
          .description(
              "One empty FlowFile for each run with an error that needs a change to the"
                  + " configuration or to the Data structure: 3xx, 4xx except 408 and 429, a"
                  + " request the web client refuses, a response that is not JSON or does not"
                  + " agree with the Record Schema, a page that is not a JSON array, a page that"
                  + " repeats an earlier page of the run, and a run that reaches Max Pages.")
          .build();

  static final Relationship FAILURE =
      new Relationship.Builder()
          .name("failure")
          .description(
              "One empty FlowFile for each run with an error that the next run can repair: 408,"
                  + " 429, 5xx, a failed connection, a timeout.")
          .build();

  private static final List<PropertyDescriptor> PROPERTIES =
      List.of(
          WEB_CLIENT_SERVICE,
          BODY_READ_TIMEOUT,
          BASE_URL,
          API_CALL,
          AUTHENTICATION_TYPE,
          BASIC_AUTH_USERNAME,
          BASIC_AUTH_PASSWORD,
          RECORD_SCHEMA,
          MAX_PAGES);

  private static final Set<Relationship> RELATIONSHIPS = Set.of(SUCCESS, STOP, FAILURE);

  private volatile PageLoop pageLoop;

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
        results.add(result(API_CALL.getDisplayName(), violation));
      }
    }
    if (sendsCredentialsInClearText(context)) {
      results.add(
          result(BASE_URL.getDisplayName(), "must use https when the request authenticates"));
    }
    return results;
  }

  private static boolean sendsCredentialsInClearText(ValidationContext context) {
    AuthenticationType type =
        context.getProperty(AUTHENTICATION_TYPE).asAllowableValue(AuthenticationType.class);
    String scheme = URI.create(context.getProperty(BASE_URL).getValue()).getScheme();
    return type != AuthenticationType.NONE && "http".equalsIgnoreCase(scheme);
  }

  @OnScheduled
  public void readConfiguration(ProcessContext context)
      throws InvalidApiCallException, InvalidRecordSchemaException {
    ApiCall apiCall = ApiCallParser.parse(context.getProperty(API_CALL).getValue());
    RecordSchema recordSchema = RecordSchema.compile(context.getProperty(RECORD_SCHEMA).getValue());
    ApiCallClient client =
        new ApiCallClient(
            context
                .getProperty(WEB_CLIENT_SERVICE)
                .asControllerService(WebClientServiceProvider.class)
                .getWebClientService(),
            context.getProperty(BASE_URL).getValue(),
            context.getProperty(BODY_READ_TIMEOUT).asDuration());
    pageLoop =
        new PageLoop(
            apiCall,
            client,
            recordSchema,
            context.getProperty(MAX_PAGES).asInteger(),
            this::isScheduled,
            getLogger());
  }

  @Override
  public void onTrigger(ProcessContext context, ProcessSession session) {
    Authentication authentication =
        context
            .getProperty(AUTHENTICATION_TYPE)
            .asAllowableValue(AuthenticationType.class)
            .authentication(context);

    pageLoop.run(session, authentication);
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
