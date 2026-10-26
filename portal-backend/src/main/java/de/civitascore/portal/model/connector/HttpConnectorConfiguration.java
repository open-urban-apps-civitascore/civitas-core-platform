package de.civitascore.portal.model.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import java.util.List;

/** Connector configuration of an HTTP/REST Data source. */
@Schema(name = "HttpConnector", description = "Connector of an HTTP/REST Data source.")
public record HttpConnectorConfiguration(
    @Schema(
            description =
                "API host: an http or https URL, which every API call path extends. Embedded"
                    + " credentials and a trailing '/' are removed; a query or fragment is"
                    + " rejected. Use authentication for credentials; any authentication other than"
                    + " NONE needs an https URL.",
            example = "https://api.example.org/v1")
        @NotNull(groups = OnRelease.class, message = "'baseUrl' is required") String baseUrl,
    @Schema(
            description = "Authentication of every API call. Absent or null is NONE.",
            oneOf = {
              None.class,
              Basic.class,
              BearerToken.class,
              ApiToken.class,
              OAuth2.class,
              ClientCertificate.class
            })
        @Valid Authentication authentication,
    @Schema(description = "The API calls, in the order the client sent them.")
        @NotEmpty(
            groups = OnRelease.class,
            message = "'apiCalls' is required and must be a non-empty list")
        List<@Valid ApiCall> apiCalls)
    implements ConnectorConfiguration {

  /** A header name as RFC 9110 defines a token. */
  public static final String HEADER_NAME = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";

  /**
   * A JSON path of member names and array indexes only, so that the processor evaluates no filter
   * or script expression.
   */
  static final String JSON_PATH = "\\$(\\.[A-Za-z_][A-Za-z0-9_-]*|\\['[^'\\\\]+'\\]|\\[[0-9]+\\])+";

  private static final String JSON_PATH_MESSAGE =
      " must start with '$' and hold only member names and array indexes, as in $.data[0].id or"
          + " $['next cursor']";

  public HttpConnectorConfiguration {
    baseUrl = ConnectorUris.httpBaseUrl(baseUrl, "baseUrl");
    authentication = authentication == null ? new None() : authentication;
    apiCalls = ConnectorValues.listOf(apiCalls, "apiCalls");
  }

  @Override
  @Schema(allowableValues = "HTTP", requiredMode = Schema.RequiredMode.REQUIRED)
  public ConnectorType connectorType() {
    return ConnectorType.HTTP;
  }

  /** How the connector authenticates against the API. */
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = JsonMergePatch.NESTED_TYPE_PROPERTY)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = None.class, name = None.TYPE),
    @JsonSubTypes.Type(value = Basic.class, name = Basic.TYPE),
    @JsonSubTypes.Type(value = BearerToken.class, name = BearerToken.TYPE),
    @JsonSubTypes.Type(value = ApiToken.class, name = ApiToken.TYPE),
    @JsonSubTypes.Type(value = OAuth2.class, name = OAuth2.TYPE),
    @JsonSubTypes.Type(value = ClientCertificate.class, name = ClientCertificate.TYPE),
  })
  @Schema(
      name = "HttpConnectorAuthentication",
      discriminatorProperty = JsonMergePatch.NESTED_TYPE_PROPERTY,
      discriminatorMapping = {
        @DiscriminatorMapping(value = None.TYPE, schema = None.class),
        @DiscriminatorMapping(value = Basic.TYPE, schema = Basic.class),
        @DiscriminatorMapping(value = BearerToken.TYPE, schema = BearerToken.class),
        @DiscriminatorMapping(value = ApiToken.TYPE, schema = ApiToken.class),
        @DiscriminatorMapping(value = OAuth2.TYPE, schema = OAuth2.class),
        @DiscriminatorMapping(value = ClientCertificate.TYPE, schema = ClientCertificate.class),
      })
  public sealed interface Authentication
      permits None, Basic, BearerToken, ApiToken, OAuth2, ClientCertificate {
    @JsonProperty(JsonMergePatch.NESTED_TYPE_PROPERTY)
    String type();
  }

  /** No authentication. */
  @Schema(name = "HttpConnectorNoAuthentication", description = "No authentication.")
  public record None() implements Authentication {
    static final String TYPE = "NONE";

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }

  /** HTTP basic authentication. */
  @Schema(name = "HttpConnectorBasicAuthentication", description = "HTTP basic authentication.")
  public record Basic(
      @Schema(description = "Basic auth username. Must not contain ':'.", example = "reader")
          @NotNull(groups = OnRelease.class, message = "'username' is required") @Pattern(regexp = "^[^:]*$", message = "'username' must not contain ':'") String username,
      @Schema(
              description =
                  "Basic auth password. Responses show \"********\" for a stored password.",
              implementation = String.class,
              accessMode = Schema.AccessMode.WRITE_ONLY,
              format = "password",
              example = "secret")
          @NotNull(groups = OnRelease.class, message = "'password' is required") Secret password)
      implements Authentication {
    static final String TYPE = "BASIC";

    public Basic {
      username = ConnectorValues.blankToNull(username);
    }

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }

  /** A bearer token in the Authorization header. */
  @Schema(
      name = "HttpConnectorBearerTokenAuthentication",
      description = "A bearer token, sent as 'Authorization: Bearer <token>'.")
  public record BearerToken(
      @Schema(
              description = "The token. Responses show \"********\" for a stored token.",
              implementation = String.class,
              accessMode = Schema.AccessMode.WRITE_ONLY,
              format = "password",
              example = "secret")
          @NotNull(groups = OnRelease.class, message = "'token' is required") Secret token)
      implements Authentication {
    static final String TYPE = "BEARER_TOKEN";

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }

  /** An API token in a header or a query parameter of each request. */
  @Schema(
      name = "HttpConnectorApiTokenAuthentication",
      description = "An API token in a header or a query parameter of each request.")
  public record ApiToken(
      @Schema(
              description =
                  "Name of the header or query parameter that carries the token. It differs from"
                      + " each header or query parameter at the same place of every API call,"
                      + " pagination included. As a header name, it follows the header name rules"
                      + " of an API call, but Authorization, Proxy-Authorization and Cookie are"
                      + " allowed.",
              example = "X-API-Key")
          @NotNull(groups = OnRelease.class, message = "'name' is required") String name,
      @Schema(
              description = "The token. Responses show \"********\" for a stored token.",
              implementation = String.class,
              accessMode = Schema.AccessMode.WRITE_ONLY,
              format = "password",
              example = "secret")
          @NotNull(groups = OnRelease.class, message = "'value' is required") Secret value,
      @Schema(
              description =
                  "Where each request sends the token. A token in a query parameter appears in the"
                      + " access logs of the API host.",
              example = "HEADER")
          @NotNull(groups = OnRelease.class, message = "'in' is required") ParameterLocation in)
      implements Authentication {
    static final String TYPE = "API_TOKEN";

    public ApiToken {
      name = ConnectorValues.blankToNull(name);
    }

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }

  /** OAuth 2.0 with the client credentials grant. */
  @Schema(
      name = "HttpConnectorOAuth2Authentication",
      description =
          "OAuth 2.0 with the client credentials grant: an access token from the token endpoint,"
              + " sent as 'Authorization: Bearer <token>'.")
  public record OAuth2(
      @Schema(
              description =
                  "Token endpoint of the authorization server: an https URL. Embedded credentials"
                      + " are removed; a query or fragment is rejected.",
              example = "https://auth.example.org/oauth2/token")
          @NotNull(groups = OnRelease.class, message = "'tokenUrl' is required") @Pattern(regexp = "(?i)https:.*", message = "'tokenUrl' must use https")
          String tokenUrl,
      @Schema(description = "Client ID at the authorization server.", example = "civitas-core")
          @NotNull(groups = OnRelease.class, message = "'clientId' is required") String clientId,
      @Schema(
              description =
                  "Client secret at the authorization server. Responses show \"********\" for a"
                      + " stored secret.",
              implementation = String.class,
              accessMode = Schema.AccessMode.WRITE_ONLY,
              format = "password",
              example = "secret")
          @NotNull(groups = OnRelease.class, message = "'clientSecret' is required") Secret clientSecret,
      @Schema(
              description =
                  "Scope of the access token, space-separated. Absent or null requests no scope.",
              example = "read:stations")
          String scope)
      implements Authentication {
    static final String TYPE = "OAUTH2";

    public OAuth2 {
      tokenUrl = ConnectorUris.httpUrl(tokenUrl, "tokenUrl");
      clientId = ConnectorValues.blankToNull(clientId);
      scope = ConnectorValues.blankToNull(scope);
    }

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }

  /** A TLS client certificate. */
  @Schema(
      name = "HttpConnectorClientCertificateAuthentication",
      description = "A TLS client certificate with its private key.")
  public record ClientCertificate(
      @Schema(
              description =
                  "The client certificate in PEM, optionally followed by its chain. Responses show"
                      + " \"********\" for a stored certificate.",
              implementation = String.class,
              accessMode = Schema.AccessMode.WRITE_ONLY,
              format = "password",
              example = "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----")
          @NotNull(groups = OnRelease.class, message = "'certificate' is required") Secret certificate,
      @Schema(
              description =
                  "The private key of the certificate in PEM, without a passphrase. Responses show"
                      + " \"********\" for a stored key.",
              implementation = String.class,
              accessMode = Schema.AccessMode.WRITE_ONLY,
              format = "password",
              example = "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----")
          @NotNull(groups = OnRelease.class, message = "'privateKey' is required") Secret privateKey)
      implements Authentication {
    static final String TYPE = "CLIENT_CERTIFICATE";

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }

  /** A named GET request against the API host. */
  @Schema(name = "HttpConnectorApiCall", description = "A GET request against the API host.")
  public record ApiCall(
      @Schema(
              description =
                  "Name of the API call, unique within the connector. A source node of a"
                      + " pipeline refers to the API call by this name, so a rename breaks that"
                      + " reference.",
              example = "Get all weather stations")
          @NotNull(groups = OnRelease.class, message = "'name' is required") String name,
      @JsonProperty(access = JsonProperty.Access.READ_ONLY)
          @Schema(
              description = "HTTP method. Always GET; ignored in a request.",
              accessMode = Schema.AccessMode.READ_ONLY,
              example = "GET")
          Method method,
      @Schema(
              description =
                  "Path of the API call below the API host. Starts with '/' and contains only"
                      + " letters, digits and '-', '_', '.', '~', ':', '/'.",
              example = "/stations")
          @NotNull(groups = OnRelease.class, message = "'path' is required") @Pattern(
              regexp = "^/[A-Za-z0-9._~:/-]*$",
              message =
                  "'path' must start with '/' and contain only letters, digits and '-', '_', '.',"
                      + " '~', ':', '/'")
          String path,
      @Schema(
              description =
                  "Query parameters. Name and value are plain text, sent as given and"
                      + " percent-encoded by the processor. Values are stored and returned in"
                      + " clear text, so they must not hold secrets.")
          List<@Valid Parameter> queryParameters,
      @Schema(
              description =
                  "Request headers, sent as given. A name is a header token and not"
                      + " Authorization, Proxy-Authorization, Cookie, Connection, Content-Length,"
                      + " Expect, Host or Upgrade. A value holds only printable ISO-8859-1"
                      + " characters, spaces and tabs. Values are stored and returned in clear"
                      + " text, so they must not hold secrets; use authentication for"
                      + " credentials.")
          List<
                  @Valid @ConvertGroup(from = Default.class, to = Parameter.HeaderRules.class)
                  Parameter>
              headers,
      @Schema(
              description = "Pagination of the API call. Absent or null is no pagination.",
              oneOf = {PageNumber.class, LimitOffset.class, Cursor.class})
          @Valid Pagination pagination) {

    public ApiCall {
      name = ConnectorValues.blankToNull(name);
      method = Method.GET;
      path = ConnectorValues.blankToNull(path);
      queryParameters = ConnectorValues.listOf(queryParameters, "queryParameters");
      headers = ConnectorValues.listOf(headers, "headers");
    }
  }

  /** The HTTP method of an API call. */
  public enum Method {
    GET
  }

  /** Where a request sends a named value. */
  public enum ParameterLocation {
    QUERY,
    HEADER
  }

  /** A query parameter or a header of an API call. */
  @Schema(name = "HttpConnectorParameter", description = "A query parameter or a header.")
  public record Parameter(
      @Schema(description = "Name, as plain text.", example = "status")
          @NotNull(groups = OnRelease.class, message = "'name' is required") @Pattern(
              groups = HeaderRules.class,
              regexp = HEADER_NAME,
              message = "'name' must be a valid header name")
          String name,
      @Schema(description = "Value, as plain text. Can be empty.", example = "active")
          @Pattern(
              groups = HeaderRules.class,
              regexp = "[\\t\\x20-\\x7E\\x{80}-\\x{FF}]*",
              message = "'value' must hold only printable ISO-8859-1 characters, spaces and tabs")
          String value) {

    public Parameter {
      name = ConnectorValues.blankToNull(name);
    }

    /** The rules that a parameter meets as a header, beyond those of a query parameter. */
    interface HeaderRules extends Default {}
  }

  /** How the connector requests the pages of a paged API call. */
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = JsonMergePatch.NESTED_TYPE_PROPERTY)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = PageNumber.class, name = PageNumber.TYPE),
    @JsonSubTypes.Type(value = LimitOffset.class, name = LimitOffset.TYPE),
    @JsonSubTypes.Type(value = Cursor.class, name = Cursor.TYPE),
  })
  @Schema(
      name = "HttpConnectorPagination",
      discriminatorProperty = JsonMergePatch.NESTED_TYPE_PROPERTY,
      discriminatorMapping = {
        @DiscriminatorMapping(value = PageNumber.TYPE, schema = PageNumber.class),
        @DiscriminatorMapping(value = LimitOffset.TYPE, schema = LimitOffset.class),
        @DiscriminatorMapping(value = Cursor.TYPE, schema = Cursor.class),
      })
  public sealed interface Pagination permits PageNumber, LimitOffset, Cursor {
    @JsonProperty(JsonMergePatch.NESTED_TYPE_PROPERTY)
    String type();
  }

  /** Page-number pagination, with page number and page size as query parameters. */
  @Schema(
      name = "HttpConnectorPageNumberPagination",
      description = "Page-number pagination, with page number and page size as query parameters.")
  public record PageNumber(
      @Schema(description = "Query parameter that carries the page number.", example = "page")
          @NotNull(groups = OnRelease.class, message = "'pageParameter' is required") String pageParameter,
      @Schema(description = "Query parameter that carries the page size.", example = "size")
          @NotNull(groups = OnRelease.class, message = "'pageSizeParameter' is required") String pageSizeParameter,
      @Schema(
              description =
                  "Number of records per page. At least 1. It must not be larger than the maximum"
                      + " page size of the API. With a larger page size, the run stops after the"
                      + " first page without an error.",
              example = "100")
          @NotNull(groups = OnRelease.class, message = "'pageSize' is required") @Min(value = 1, message = "'pageSize' must be at least 1") Integer pageSize,
      @Schema(
              description = "First page number. Absent or null is 0.",
              defaultValue = "0",
              example = "0")
          @Min(value = 0, message = "'startPage' must be at least 0") Integer startPage,
      @Schema(
              description = "Last page number, at least startPage. Absent or null is no end page.",
              example = "10")
          @Min(value = 0, message = "'endPage' must be at least 0") Integer endPage)
      implements Pagination {
    static final String TYPE = "PAGE_NUMBER";
    private static final int DEFAULT_START_PAGE = 0;

    public PageNumber {
      pageParameter = ConnectorValues.blankToNull(pageParameter);
      pageSizeParameter = ConnectorValues.blankToNull(pageSizeParameter);
      startPage = startPage == null ? DEFAULT_START_PAGE : startPage;
    }

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }

  /** Limit/offset pagination, with limit and offset as query parameters. */
  @Schema(
      name = "HttpConnectorLimitOffsetPagination",
      description =
          "Limit/offset pagination, with limit and offset as query parameters. Without a limit,"
              + " the API pages by its default page size, and the offset grows by the number of"
              + " records of each response.")
  public record LimitOffset(
      @Schema(
              description =
                  "Query parameter that carries the limit. Required with limit; absent or null"
                      + " sends no limit.",
              example = "limit")
          String limitParameter,
      @Schema(description = "Query parameter that carries the offset.", example = "offset")
          @NotNull(groups = OnRelease.class, message = "'offsetParameter' is required") String offsetParameter,
      @Schema(
              description =
                  "Number of records per request. At least 1. Required with limitParameter.",
              example = "100")
          @Min(value = 1, message = "'limit' must be at least 1") Integer limit,
      @Schema(
              description = "Offset of the first request. At least 0. Absent or null is 0.",
              defaultValue = "0",
              example = "0")
          @Min(value = 0, message = "'offset' must be at least 0") Integer offset,
      @Schema(
              description =
                  "JSON path in the response to the number of records, or to an array whose length"
                      + " is that number. Only member names and array indexes. Absent or null"
                      + " counts the elements of the response, which must then be an array.",
              example = "$.meta.count")
          @Pattern(regexp = JSON_PATH, message = "'countPath'" + JSON_PATH_MESSAGE) String countPath)
      implements Pagination {
    static final String TYPE = "LIMIT_OFFSET";
    private static final int DEFAULT_OFFSET = 0;

    public LimitOffset {
      limitParameter = ConnectorValues.blankToNull(limitParameter);
      offsetParameter = ConnectorValues.blankToNull(offsetParameter);
      offset = offset == null ? DEFAULT_OFFSET : offset;
      countPath = ConnectorValues.blankToNull(countPath);
    }

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }

  /** Cursor pagination, with the cursor of the next request taken from each response. */
  @Schema(
      name = "HttpConnectorCursorPagination",
      description =
          "Cursor pagination: each request sends the cursor that the previous response holds. Each"
              + " response is one record, so the Data structure describes the whole response,"
              + " cursor included.")
  public record Cursor(
      @Schema(
              description =
                  "JSON path in the response to the cursor of the next request. Only member names"
                      + " and array indexes.",
              example = "$.next_cursor")
          @NotNull(groups = OnRelease.class, message = "'cursorPath' is required") @Pattern(regexp = JSON_PATH, message = "'cursorPath'" + JSON_PATH_MESSAGE) String cursorPath,
      @Schema(
              description =
                  "Name of the query parameter or header that carries the cursor. As a header"
                      + " name, it follows the header name rules of an API call.",
              example = "cursor")
          @NotNull(groups = OnRelease.class, message = "'cursorParameter' is required") String cursorParameter,
      @Schema(description = "Where each request sends the cursor.", example = "QUERY")
          @NotNull(groups = OnRelease.class, message = "'cursorIn' is required") ParameterLocation cursorIn,
      @Schema(
              description =
                  "Query parameter that carries the limit. Required with limit; absent or null"
                      + " sends no limit.",
              example = "limit")
          String limitParameter,
      @Schema(
              description =
                  "Number of records per request. At least 1. Required with limitParameter.",
              example = "100")
          @Min(value = 1, message = "'limit' must be at least 1") Integer limit)
      implements Pagination {
    static final String TYPE = "CURSOR";

    public Cursor {
      cursorPath = ConnectorValues.blankToNull(cursorPath);
      cursorParameter = ConnectorValues.blankToNull(cursorParameter);
      limitParameter = ConnectorValues.blankToNull(limitParameter);
    }

    @Override
    @Schema(allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
    public String type() {
      return TYPE;
    }
  }
}
