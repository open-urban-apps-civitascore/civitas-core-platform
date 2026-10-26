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
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Completes the OpenAPI description that springdoc builds from the controllers: the OAuth2 security
 * scheme, the relative server URL and the request body schema of each PATCH operation. The error
 * responses come from {@link
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

  /** Replace auto-generated localhost URL with a relative base path. */
  @Bean
  public OpenApiCustomizer relativeServerUrlCustomizer() {
    return openApi ->
        openApi.setServers(List.of(new io.swagger.v3.oas.models.servers.Server().url("/v1")));
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
