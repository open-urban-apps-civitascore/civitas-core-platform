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
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration that customizes the generated API documentation. Adds OAuth2
 * security schemes, normalizes operation IDs, replaces entity name placeholders, and derives PATCH
 * schemas from PUT input DTOs. The error responses come from {@link
 * de.civitascore.portal.configuration.openapi.ProblemResponsesCustomizer}.
 */
@Configuration
public class OpenApiConfig {

  private final KeycloakProperties keycloakProperties;
  private final OpenApiProperties openApiProperties;

  public OpenApiConfig(
      final KeycloakProperties keycloakProperties, final OpenApiProperties openApiProperties) {
    this.keycloakProperties = keycloakProperties;
    this.openApiProperties = openApiProperties;
  }

  private static final String APPLICATION_JSON = "application/json";

  /**
   * Creates the base OpenAPI definition with info metadata, license, contact, external docs, and
   * OAuth2 security scheme derived from Keycloak configuration. The scheme uses {@code
   * openapi.oauth-server-url} as the Keycloak base URL when it is set, else {@code
   * keycloak.auth-server-url}.
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

    String authServerUrl =
        openApiProperties.oauthServerUrl() != null && !openApiProperties.oauthServerUrl().isBlank()
            ? openApiProperties.oauthServerUrl()
            : keycloakProperties.authServerUrl();
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
                              operation.getResponses().values().stream()
                                  .filter(response -> response.get$ref() == null)
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
                                            .schema(
                                                new Schema<>()
                                                    .types(Set.of("string"))
                                                    .format("uuid")));
                              }
                            }
                          });
                });
  }

  /**
   * Replaces the request body of PATCH endpoints with a copy of the InputDTO schema that has all
   * fields optional (no {@code required} array). The InputDTO is the one used by PUT on the same
   * path, or, without a PUT, the one the PATCH declares itself.
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
    if (patchOp == null || patchOp.getRequestBody() == null) return;

    var sourceOp = putOp != null && putOp.getRequestBody() != null ? putOp : patchOp;
    String sourceRef = extractSchemaRef(sourceOp);
    if (sourceRef == null) return;

    String inputName = sourceRef.substring(sourceRef.lastIndexOf('/') + 1);
    if (inputName.equals("JsonNode")) return;
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
}
