package de.civitascore.portal.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * OpenAPI/Swagger configuration that customizes the generated API documentation. Adds OAuth2
 * security schemes, normalizes operation IDs, replaces entity name placeholders, provides RFC 9457
 * ProblemDetail examples, and derives PATCH schemas from PUT input DTOs.
 */
@Configuration
public class OpenApiConfig {

  private final KeycloakProperties keycloakProperties;

  public OpenApiConfig(final KeycloakProperties keycloakProperties) {
    this.keycloakProperties = keycloakProperties;
  }

  private static final String APPLICATION_JSON = "application/json";
  private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
  private static final String EXAMPLE_PATH = "/v1/datasets";

  /**
   * Creates the base OpenAPI definition with info metadata, license, contact, external docs, and
   * OAuth2 security scheme derived from Keycloak configuration.
   *
   * @return the configured OpenAPI specification object
   */
  @Bean
  public OpenAPI customOpenAPI() {
    OpenAPI openAPI =
        new OpenAPI()
            .info(
                new Info()
                    .title("CIVITAS/CORE Data Management API")
                    .summary("Smart city data management REST API")
                    .version("2.0.0")
                    .description(
                        "REST API for the CIVITAS/CORE smart city data management platform. "
                            + "Manages datasets, data sources, data structures, users, groups, "
                            + "roles, and permissions.")
                    .license(new License().name("EUPL-1.2").url("https://eupl.eu/1.2/en/"))
                    .contact(
                        new Contact()
                            .name("CIVITAS Connect")
                            .url("https://civitasconnect.digital")))
            .externalDocs(
                new ExternalDocumentation()
                    .description("CIVITAS/CORE Developer Documentation")
                    .url("https://docs.core.civitasconnect.digital/docs_v2/Development/intro"));

    String authServerUrl = keycloakProperties.authServerUrl();
    String realm = keycloakProperties.realm();
    if (authServerUrl != null && !authServerUrl.isBlank() && realm != null && !realm.isBlank()) {
      String authUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
      String tokenUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

      openAPI
          .components(
              new Components()
                  .addSecuritySchemes(
                      "oauth2",
                      new SecurityScheme()
                          .type(SecurityScheme.Type.OAUTH2)
                          .description("Keycloak OAuth2 Authorization Code with PKCE")
                          .flows(
                              new OAuthFlows()
                                  .authorizationCode(
                                      new OAuthFlow()
                                          .authorizationUrl(authUrl)
                                          .tokenUrl(tokenUrl)
                                          .scopes(new Scopes())))))
          .addSecurityItem(new SecurityRequirement().addList("oauth2"));
    }

    return openAPI;
  }

  /**
   * Replaces {@code {entity}}, {@code {entities}}, and {@code {Entity}} placeholders in operation
   * summaries, response descriptions, and operationIds using the tag name.
   */
  @Bean
  public OpenApiCustomizer placeholderReplacementCustomizer() {
    return openApi ->
        openApi
            .getPaths()
            .values()
            .forEach(
                pathItem ->
                    pathItem
                        .readOperations()
                        .forEach(
                            operation -> {
                              if (operation.getTags() == null || operation.getTags().isEmpty()) {
                                return;
                              }
                              String tag = operation.getTags().get(0);
                              String plural = tag.toLowerCase();
                              String singular =
                                  plural.endsWith("s")
                                      ? plural.substring(0, plural.length() - 1)
                                      : plural;
                              String capitalized =
                                  singular.substring(0, 1).toUpperCase() + singular.substring(1);
                              // Derive PascalCase from original tag: "DataSources" → "DataSource",
                              // "Data Structures" → "DataStructure"
                              String strippedTag =
                                  tag.endsWith("s") ? tag.substring(0, tag.length() - 1) : tag;
                              String entityPascal = strippedTag.replace(" ", "");
                              if (operation.getSummary() != null) {
                                operation.setSummary(
                                    operation
                                        .getSummary()
                                        .replace("{entities}", plural)
                                        .replace("{entity}", singular)
                                        .replace("{Entity}", capitalized));
                              }
                              if (operation.getDescription() != null) {
                                operation.setDescription(
                                    operation
                                        .getDescription()
                                        .replace("{entities}", plural)
                                        .replace("{entity}", singular)
                                        .replace("{Entity}", capitalized));
                              }
                              if (operation.getOperationId() != null) {
                                operation.setOperationId(
                                    operation.getOperationId().replace("{Entity}", entityPascal));
                              }
                              operation
                                  .getResponses()
                                  .values()
                                  .forEach(
                                      response ->
                                          response.setDescription(
                                              response
                                                  .getDescription()
                                                  .replace("{entities}", plural)
                                                  .replace("{entity}", singular)
                                                  .replace("{Entity}", capitalized)));
                            }));
  }

  /**
   * Sets operationIds for base CRUD endpoints (1- or 2-segment paths). Non-CRUD endpoints must set
   * operationId manually via {@code @Operation(operationId = "...")} — the customizer skips them.
   */
  @Bean
  public OpenApiCustomizer operationIdCustomizer() {
    return openApi ->
        openApi
            .getPaths()
            .forEach(
                (path, pathItem) ->
                    pathItem
                        .readOperationsMap()
                        .forEach(
                            (method, operation) -> {
                              if (operation.getTags() == null || operation.getTags().isEmpty()) {
                                return;
                              }
                              // Skip if operationId was explicitly set via @Operation.
                              // SpringDoc auto-generates IDs like "getById", "getAll_1",
                              // "create_2" — overwrite those but preserve manual ones.
                              String existingId = operation.getOperationId();
                              if (existingId != null
                                  && !existingId.matches(
                                      "(getById|getAll|create|update|delete|patch)(_\\d+)?")) {
                                return;
                              }

                              String[] segments = path.substring(1).split("/");
                              // Only handle /{resource} and /{resource}/{param}
                              if (segments.length > 2) return;
                              if (segments.length == 2 && !segments[1].startsWith("{")) return;

                              String tag = operation.getTags().get(0);
                              String stripped =
                                  tag.endsWith("s") ? tag.substring(0, tag.length() - 1) : tag;
                              String singular = stripped.replace(" ", "");
                              String plural = tag.replace(" ", "");

                              String operationId =
                                  switch (method) {
                                    case GET ->
                                        segments.length == 1 ? "list" + plural : "get" + singular;
                                    case POST -> "create" + singular;
                                    case PUT -> "update" + singular;
                                    case PATCH -> "patch" + singular;
                                    case DELETE -> "delete" + singular;
                                    default -> null;
                                  };
                              if (operationId != null) {
                                operation.setOperationId(operationId);
                              }
                            }));
  }

  /** Replace auto-generated localhost URL with a relative base path. */
  @Bean
  public OpenApiCustomizer relativeServerUrlCustomizer() {
    return openApi ->
        openApi.setServers(List.of(new io.swagger.v3.oas.models.servers.Server().url("/v1")));
  }

  /**
   * Ensures all path parameters referenced in URL templates are declared as {@code @Parameter} on
   * the operation. Adds missing path parameters with UUID schema so the generated spec is valid.
   */
  @Bean
  public OpenApiCustomizer missingPathParameterCustomizer() {
    Pattern pathParamPattern = Pattern.compile("\\{([^}]+)}");
    return openApi ->
        openApi
            .getPaths()
            .forEach(
                (path, pathItem) -> {
                  Matcher matcher = pathParamPattern.matcher(path);
                  List<String> pathParams = new ArrayList<>();
                  while (matcher.find()) {
                    pathParams.add(matcher.group(1));
                  }
                  if (pathParams.isEmpty()) {
                    return;
                  }
                  pathItem
                      .readOperations()
                      .forEach(
                          operation -> {
                            Set<String> declared =
                                operation.getParameters() == null
                                    ? Set.of()
                                    : operation.getParameters().stream()
                                        .filter(p -> "path".equals(p.getIn()))
                                        .map(Parameter::getName)
                                        .collect(Collectors.toSet());
                            for (String param : pathParams) {
                              if (!declared.contains(param)) {
                                if (operation.getParameters() == null) {
                                  operation.setParameters(new ArrayList<>());
                                }
                                operation
                                    .getParameters()
                                    .add(
                                        new Parameter()
                                            .name(param)
                                            .in("path")
                                            .required(true)
                                            .schema(new Schema<>().type("string").format("uuid")));
                              }
                            }
                          });
                });
  }

  /**
   * Adds global 401/403/500 responses to all operations and normalizes all ProblemDetail error
   * responses to use {@code application/problem+json} (RFC 9457). Other error codes (400, 404, 409)
   * come from {@code @ApiResponse} annotations on controllers.
   */
  @Bean
  @Order(1)
  public OpenApiCustomizer globalResponseCustomizer() {
    return openApi ->
        openApi
            .getPaths()
            .values()
            .forEach(
                pathItem ->
                    pathItem
                        .readOperations()
                        .forEach(
                            operation -> {
                              operation
                                  .getResponses()
                                  .addApiResponse(
                                      "401",
                                      new ApiResponse()
                                          .description("Authentication required")
                                          .content(newProblemContent()));
                              operation
                                  .getResponses()
                                  .addApiResponse(
                                      "403",
                                      new ApiResponse()
                                          .description("Insufficient privileges")
                                          .content(newProblemContent()));
                              operation
                                  .getResponses()
                                  .addApiResponse(
                                      "500",
                                      new ApiResponse()
                                          .description("Internal server error")
                                          .content(newProblemContent()));

                              operation
                                  .getResponses()
                                  .values()
                                  .forEach(this::normalizeProblemContentType);
                            }));
  }

  /**
   * Extracts common error responses (401, 403, 500) into {@code components/responses} and replaces
   * inline occurrences with {@code $ref}s.
   */
  @Bean
  @Order(2)
  public OpenApiCustomizer sharedResponsesCustomizer() {
    Map<String, String> sharedResponses =
        orderedMap(
            "401", "Unauthorized",
            "403", "Forbidden",
            "500", "InternalServerError");

    return openApi -> {
      // Capture the first inline occurrence of each code as the shared definition
      openApi.getPaths().values().stream()
          .flatMap(pathItem -> pathItem.readOperations().stream())
          .findFirst()
          .ifPresent(
              operation ->
                  sharedResponses.forEach(
                      (code, name) -> {
                        ApiResponse inline = operation.getResponses().get(code);
                        if (inline != null && inline.get$ref() == null) {
                          openApi
                              .getComponents()
                              .addResponses(
                                  name,
                                  new ApiResponse()
                                      .description(inline.getDescription())
                                      .content(newProblemContent()));
                        }
                      }));

      // Replace all inline occurrences with $refs
      openApi
          .getPaths()
          .values()
          .forEach(
              pathItem ->
                  pathItem
                      .readOperations()
                      .forEach(
                          operation ->
                              sharedResponses.forEach(
                                  (code, name) -> {
                                    ApiResponse existing = operation.getResponses().get(code);
                                    if (existing != null && existing.get$ref() == null) {
                                      operation
                                          .getResponses()
                                          .addApiResponse(
                                              code,
                                              new ApiResponse()
                                                  .$ref("#/components/responses/" + name));
                                    }
                                  })));
    };
  }

  /**
   * Replaces the opaque {@code JsonNode} request body on PATCH endpoints with the same InputDTO
   * schema used by PUT, but with all fields optional (no {@code required} array).
   */
  @Bean
  public OpenApiCustomizer patchSchemaCustomizer() {
    return openApi -> {
      openApi.getPaths().values().forEach(pathItem -> derivePatchSchema(openApi, pathItem));
      // Remove the now-unused JsonNode schema
      if (openApi.getComponents().getSchemas() != null) {
        openApi.getComponents().getSchemas().remove("JsonNode");
      }
    };
  }

  private void derivePatchSchema(OpenAPI openApi, PathItem pathItem) {
    var opsMap = pathItem.readOperationsMap();
    var putOp = opsMap.get(PathItem.HttpMethod.PUT);
    var patchOp = opsMap.get(PathItem.HttpMethod.PATCH);
    if (putOp == null || patchOp == null) return;
    if (putOp.getRequestBody() == null || patchOp.getRequestBody() == null) return;

    String putRef = extractSchemaRef(putOp);
    if (putRef == null) return;

    String inputName = putRef.substring(putRef.lastIndexOf('/') + 1);
    String patchName = "Patch" + inputName;

    Schema<?> originalSchema = openApi.getComponents().getSchemas().get(inputName);
    if (originalSchema == null) return;

    // Clone the schema: same properties, no required → all fields optional
    Schema<?> patchSchema = new Schema<>();
    patchSchema.setType(originalSchema.getType());
    patchSchema.setProperties(originalSchema.getProperties());
    patchSchema.setDescription(
        originalSchema.getDescription() != null
            ? originalSchema.getDescription()
            : "Partial update — only provided fields are modified.");

    openApi.getComponents().addSchemas(patchName, patchSchema);

    Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + patchName);
    patchOp
        .getRequestBody()
        .setContent(new Content().addMediaType(APPLICATION_JSON, new MediaType().schema(ref)));
  }

  private String extractSchemaRef(io.swagger.v3.oas.models.Operation operation) {
    Content content = operation.getRequestBody().getContent();
    if (content == null) return null;
    MediaType json = content.get(APPLICATION_JSON);
    if (json == null || json.getSchema() == null) return null;
    return json.getSchema().get$ref();
  }

  private Content newProblemContent() {
    Schema<?> schema = new Schema<>().$ref("#/components/schemas/ProblemDetail");
    return new Content().addMediaType(APPLICATION_PROBLEM_JSON, new MediaType().schema(schema));
  }

  /**
   * Adds realistic ProblemDetail examples (RFC 9457) with actual {@code urn:civitas:error:*} type
   * URNs to all error responses. Shared responses (401, 403, 500) get examples on the component
   * definition; inline responses (400, 404, 409) get examples per-operation.
   */
  @Bean
  @Order(3)
  public OpenApiCustomizer problemDetailExamplesCustomizer() {
    return openApi -> {
      // Add examples to shared component responses
      if (openApi.getComponents() != null && openApi.getComponents().getResponses() != null) {
        addExamplesToResponse(
            openApi.getComponents().getResponses().get("Unauthorized"),
            problemExamples(
                orderedMap(
                    "expired_token",
                    problemExample(401, "UNAUTHORIZED", "Authentication required", EXAMPLE_PATH),
                    "invalid_token",
                    problemExample(401, "INVALID_TOKEN", "JWT validation failed", EXAMPLE_PATH))));
        addExamplesToResponse(
            openApi.getComponents().getResponses().get("Forbidden"),
            problemExamples(
                orderedMap(
                    "access_denied",
                    problemExample(
                        403, "ACCESS_DENIED", "Insufficient privileges", EXAMPLE_PATH))));
        addExamplesToResponse(
            openApi.getComponents().getResponses().get("InternalServerError"),
            problemExamples(
                orderedMap(
                    "data_integrity_error",
                    problemExample(
                        500,
                        "DATA_INTEGRITY_ERROR",
                        "A database constraint was violated",
                        EXAMPLE_PATH),
                    "persistence_error",
                    problemExample(
                        500,
                        "PERSISTENCE_ERROR",
                        "An unexpected database error occurred",
                        EXAMPLE_PATH))));
      }

      // Add examples to inline error responses
      openApi
          .getPaths()
          .forEach(
              (path, pathItem) ->
                  pathItem
                      .readOperations()
                      .forEach(
                          operation ->
                              operation
                                  .getResponses()
                                  .forEach(
                                      (code, response) -> {
                                        if (response.get$ref() != null) return;
                                        Map<String, Example> examples =
                                            inlineExamplesForCode(code, path);
                                        if (examples != null) {
                                          addExamplesToResponse(response, examples);
                                        }
                                      })));
    };
  }

  private Map<String, Example> inlineExamplesForCode(String code, String path) {
    return switch (code) {
      case "400" ->
          problemExamples(
              orderedMap(
                  "invalid_input",
                  problemExample(
                      400, "INVALID_INPUT", "Validation failed: name must not be blank", path),
                  "validation_failed",
                  problemExample(400, "VALIDATION_FAILED", "Request body validation failed", path),
                  "malformed_request",
                  problemExample(400, "MALFORMED_REQUEST", "Failed to read request body", path)));
      case "404" ->
          problemExamples(
              orderedMap(
                  "not_found",
                  problemExample(
                      404,
                      "NOT_FOUND",
                      "Resource not found: 550e8400-e29b-41d4-a716-446655440000",
                      path)));
      case "409" ->
          problemExamples(
              orderedMap(
                  "unique_constraint",
                  problemExample(
                      409,
                      "UNIQUE_CONSTRAINT_VIOLATION",
                      "A resource with this name already exists",
                      path),
                  "resource_in_use",
                  problemExample(
                      409,
                      "RESOURCE_IN_USE",
                      "Cannot delete: resource is referenced by other entities",
                      path),
                  "foreign_key_violation",
                  problemExample(
                      409, "FOREIGN_KEY_VIOLATION", "Referenced entity does not exist", path)));
      default -> null;
    };
  }

  private void addExamplesToResponse(ApiResponse response, Map<String, Example> examples) {
    if (response == null || response.getContent() == null) return;
    MediaType media = response.getContent().get(APPLICATION_PROBLEM_JSON);
    if (media == null) return;
    examples.forEach(media::addExamples);
  }

  private Map<String, Example> problemExamples(Map<String, Map<String, Object>> entries) {
    Map<String, Example> examples = new LinkedHashMap<>();
    entries.forEach((name, value) -> examples.put(name, new Example().summary(name).value(value)));
    return examples;
  }

  private Map<String, Object> problemExample(
      int status, String errorCode, String detail, String instance) {
    String title =
        switch (status) {
          case 400 -> "Bad Request";
          case 401 -> "Unauthorized";
          case 403 -> "Forbidden";
          case 404 -> "Not Found";
          case 409 -> "Conflict";
          case 500 -> "Internal Server Error";
          case 502 -> "Bad Gateway";
          case 504 -> "Gateway Timeout";
          default -> "Error";
        };
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("type", "urn:civitas:error:" + errorCode);
    map.put("title", title);
    map.put("status", status);
    map.put("detail", detail);
    map.put("instance", instance);
    return map;
  }

  @SuppressWarnings("unchecked")
  private <V> Map<String, V> orderedMap(Object... keyValues) {
    Map<String, V> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], (V) keyValues[i + 1]);
    }
    return map;
  }

  /**
   * Replaces {@code application/json} with {@code application/problem+json} on ProblemDetail
   * responses.
   */
  private void normalizeProblemContentType(ApiResponse response) {
    if (response.getContent() == null) return;
    MediaType jsonMedia = response.getContent().get(APPLICATION_JSON);
    if (jsonMedia == null || jsonMedia.getSchema() == null) return;
    String ref = jsonMedia.getSchema().get$ref();
    if (ref != null && ref.endsWith("/ProblemDetail")) {
      response.getContent().remove(APPLICATION_JSON);
      response.getContent().addMediaType(APPLICATION_PROBLEM_JSON, jsonMedia);
    }
  }
}
