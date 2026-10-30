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

import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.web.client.api.WebClientService;
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
      description =
          "The page number, with cursor pagination the number of the request in the run from 1,"
              + " or with limit/offset pagination the offset, on success of an API call with"
              + " pagination."),
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
          .addValidator(PropertyRules.BASE_URL)
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
          .addValidator(PropertyRules.USERNAME)
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

  static final PropertyDescriptor BEARER_TOKEN =
      new PropertyDescriptor.Builder()
          .name("Bearer Token")
          .displayName("Bearer Token")
          .description(
              "The token that the request sends as Authorization: Bearer. It holds only printable"
                  + " ISO-8859-1 characters, spaces and tabs.")
          .required(true)
          .sensitive(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.BEARER_TOKEN.name())
          .addValidator(PropertyRules.HEADER_VALUE)
          .build();

  static final PropertyDescriptor API_TOKEN_NAME =
      new PropertyDescriptor.Builder()
          .name("API Token Name")
          .displayName("API Token Name")
          .description(
              "The name of the header or the query parameter that carries the API token. It"
                  + " differs from the name of each header or query parameter of the API call and"
                  + " of its pagination.")
          .required(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.API_TOKEN.name())
          .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
          .build();

  static final PropertyDescriptor API_TOKEN_VALUE =
      new PropertyDescriptor.Builder()
          .name("API Token Value")
          .displayName("API Token Value")
          .description(
              "The API token. In a header, it holds only printable ISO-8859-1 characters, spaces"
                  + " and tabs. In the query, it is percent-encoded like each query parameter, and"
                  + " the access log of the API host shows it.")
          .required(true)
          .sensitive(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.API_TOKEN.name())
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  static final PropertyDescriptor API_TOKEN_LOCATION =
      new PropertyDescriptor.Builder()
          .name("API Token Location")
          .displayName("API Token Location")
          .description("Whether the API token goes into a header or into the query.")
          .required(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.API_TOKEN.name())
          .allowableValues(ParameterLocation.class)
          .build();

  static final PropertyDescriptor OAUTH2_TOKEN_URL =
      new PropertyDescriptor.Builder()
          .name("OAuth2 Token URL")
          .displayName("OAuth2 Token URL")
          .description(
              "The token endpoint of the client credentials grant: scheme https, a host, an"
                  + " optional port and an optional path. No user info, no query, no fragment. The"
                  + " token request goes through the Web Client Service Provider.")
          .required(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.OAUTH2.name())
          .addValidator(PropertyRules.TOKEN_URL)
          .build();

  static final PropertyDescriptor OAUTH2_CLIENT_ID =
      new PropertyDescriptor.Builder()
          .name("OAuth2 Client ID")
          .displayName("OAuth2 Client ID")
          .description("The client that the token endpoint knows.")
          .required(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.OAUTH2.name())
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  static final PropertyDescriptor OAUTH2_CLIENT_SECRET =
      new PropertyDescriptor.Builder()
          .name("OAuth2 Client Secret")
          .displayName("OAuth2 Client Secret")
          .description("The secret of that client.")
          .required(true)
          .sensitive(true)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.OAUTH2.name())
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  static final PropertyDescriptor OAUTH2_SCOPE =
      new PropertyDescriptor.Builder()
          .name("OAuth2 Scope")
          .displayName("OAuth2 Scope")
          .description(
              "The scope that the token request asks for. Without a value, it asks for none.")
          .required(false)
          .dependsOn(AUTHENTICATION_TYPE, AuthenticationType.OAUTH2.name())
          .addValidator(Validator.VALID)
          .build();

  static final PropertyDescriptor RECORD_SCHEMA =
      new PropertyDescriptor.Builder()
          .name("Record Schema")
          .displayName("Record Schema")
          .description(
              "The JSON Schema of the Data structure, with its imports inlined. Each record of a"
                  + " response must agree with it: each element of a JSON array, or, without"
                  + " pagination, with cursor pagination and with limit/offset pagination with a"
                  + " count path, a JSON object as one record. A $ref resolves only inside the"
                  + " schema or to a GeoJSON geometry of https://geojson.org/schema/, which the NAR"
                  + " holds. The processor does not start with a schema that it cannot compile.")
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
                  + " that ignores the page parameter, the cursor or the offset and changes its"
                  + " response on each request cannot make the processor send requests without end.")
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
                  + " request the web client refuses, the same statuses of the OAuth2 token"
                  + " endpoint or its 2xx without an access token, a TLS handshake that refuses"
                  + " the client certificate, a response that is not JSON or does not agree with"
                  + " the Record Schema, a page of the wrong JSON type for its pagination, a"
                  + " cursor that is not a string or a number or that holds a character a header"
                  + " cannot carry, a value at the count path that is not a record count, a page"
                  + " that repeats an earlier page or would send the same request as an earlier"
                  + " page of the run, and a run that reads Max Pages pages and meets no stop"
                  + " condition. With page-number or limit/offset pagination, a 400 or 404 on a"
                  + " page after the first ends the run and is no error.")
          .build();

  static final Relationship FAILURE =
      new Relationship.Builder()
          .name("failure")
          .description(
              "One empty FlowFile for each run with an error that the next run can repair: 408,"
                  + " 429, 5xx, a failed connection, a timeout, the same errors of the OAuth2 token"
                  + " request, and a run that NiFi stops between pages.")
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
          BEARER_TOKEN,
          API_TOKEN_NAME,
          API_TOKEN_VALUE,
          API_TOKEN_LOCATION,
          OAUTH2_TOKEN_URL,
          OAUTH2_CLIENT_ID,
          OAUTH2_CLIENT_SECRET,
          OAUTH2_SCOPE,
          RECORD_SCHEMA,
          MAX_PAGES);

  private static final Set<Relationship> RELATIONSHIPS = Set.of(SUCCESS, STOP, FAILURE);

  private final InstantSource clock;

  /** What the start built, empty while NiFi does not schedule the processor. */
  private volatile Optional<Scheduled> scheduled = Optional.empty();

  public GetHttpApiCall() {
    this(InstantSource.system());
  }

  /**
   * @param clock decides when a kept OAuth2 access token expires
   */
  GetHttpApiCall(InstantSource clock) {
    super();
    this.clock = clock;
  }

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
    Optional<ApiCall> apiCall = Optional.empty();
    try {
      apiCall = Optional.of(ApiCallParser.parse(context.getProperty(API_CALL).getValue()));
    } catch (InvalidApiCallException e) {
      for (String violation : e.violations()) {
        results.add(PropertyRules.result(API_CALL.getDisplayName(), violation));
      }
    }
    results.addAll(PropertyRules.authenticationViolations(context, apiCall));
    return results;
  }

  @OnScheduled
  public void readConfiguration(ProcessContext context)
      throws InvalidApiCallException, InvalidRecordSchemaException {
    ApiCall apiCall = ApiCallParser.parse(context.getProperty(API_CALL).getValue());
    RecordSchema recordSchema = RecordSchema.compile(context.getProperty(RECORD_SCHEMA).getValue());
    WebClientService webClient =
        context
            .getProperty(WEB_CLIENT_SERVICE)
            .asControllerService(WebClientServiceProvider.class)
            .getWebClientService();
    Duration bodyReadTimeout = context.getProperty(BODY_READ_TIMEOUT).asDuration();
    ApiCallClient client =
        new ApiCallClient(webClient, context.getProperty(BASE_URL).getValue(), bodyReadTimeout);
    scheduled =
        Optional.of(
            new Scheduled(
                new PageLoop(
                    apiCall,
                    client,
                    recordSchema,
                    context.getProperty(MAX_PAGES).asInteger(),
                    this::isScheduled,
                    getLogger()),
                context
                    .getProperty(AUTHENTICATION_TYPE)
                    .asAllowableValue(AuthenticationType.class)
                    .authentication(context, webClient, bodyReadTimeout, clock)));
  }

  /** Drops what the start built, so that a kept access token does not outlive the schedule. */
  @OnStopped
  public void dropConfiguration() {
    scheduled = Optional.empty();
  }

  @Override
  public void onTrigger(ProcessContext context, ProcessSession session) {
    Scheduled current = scheduled.orElseThrow();
    current.pageLoop().run(session, current.authentication());
  }

  private record Scheduled(PageLoop pageLoop, Authentication authentication) {}
}
