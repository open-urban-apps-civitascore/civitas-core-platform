package de.civitascore.portal.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class OpenApiConfig {

  private static final String APPLICATION_JSON = "application/json";
  private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

  @Value("${keycloak.auth-server-url:}")
  private String keycloakUrl;

  @Value("${keycloak.realm:}")
  private String realm;

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

    if (!keycloakUrl.isBlank() && !realm.isBlank()) {
      String authUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
      String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

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
   * Adds global 401/403 responses to all operations and normalizes all ProblemDetail error
   * responses to use {@code application/problem+json} (RFC 9457).
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

                              // Normalize: any response using application/json with a
                              // ProblemDetail schema should use application/problem+json
                              operation
                                  .getResponses()
                                  .forEach(
                                      (code, response) -> {
                                        if (response.getContent() == null) return;
                                        MediaType jsonMedia =
                                            response.getContent().get(APPLICATION_JSON);
                                        if (jsonMedia != null
                                            && jsonMedia.getSchema() != null
                                            && isProblemDetailRef(jsonMedia.getSchema())) {
                                          response.getContent().remove(APPLICATION_JSON);
                                          response
                                              .getContent()
                                              .addMediaType(APPLICATION_PROBLEM_JSON, jsonMedia);
                                        }
                                      });
                            }));
  }

  /**
   * Extracts common error responses (401, 403, 502, 504) into {@code components/responses} and
   * replaces inline occurrences with {@code $ref}s.
   */
  @Bean
  @Order(2)
  public OpenApiCustomizer sharedResponsesCustomizer() {
    Map<String, String> sharedResponses =
        Map.of(
            "401", "Unauthorized",
            "403", "Forbidden",
            "502", "BadGateway",
            "504", "GatewayTimeout");

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

  private boolean isProblemDetailRef(Schema<?> schema) {
    String ref = schema.get$ref();
    return ref != null && ref.endsWith("/ProblemDetail");
  }
}
