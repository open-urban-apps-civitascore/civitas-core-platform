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
            oneOf = {None.class, Basic.class})
        @Valid Authentication authentication,
    @Schema(description = "The API calls, in the order the client sent them.")
        @NotEmpty(
            groups = OnRelease.class,
            message = "'apiCalls' is required and must be a non-empty list")
        List<@Valid ApiCall> apiCalls)
    implements ConnectorConfiguration {

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
  })
  @Schema(
      name = "HttpConnectorAuthentication",
      discriminatorProperty = JsonMergePatch.NESTED_TYPE_PROPERTY,
      discriminatorMapping = {
        @DiscriminatorMapping(value = None.TYPE, schema = None.class),
        @DiscriminatorMapping(value = Basic.TYPE, schema = Basic.class),
      })
  public sealed interface Authentication permits None, Basic {
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
              oneOf = PageNumber.class)
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

  /** A query parameter or a header of an API call. */
  @Schema(name = "HttpConnectorParameter", description = "A query parameter or a header.")
  public record Parameter(
      @Schema(description = "Name, as plain text.", example = "status")
          @NotNull(groups = OnRelease.class, message = "'name' is required") @Pattern(
              groups = HeaderRules.class,
              regexp = "[!#$%&'*+.^_`|~0-9A-Za-z-]+",
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
  @JsonSubTypes({@JsonSubTypes.Type(value = PageNumber.class, name = PageNumber.TYPE)})
  @Schema(
      name = "HttpConnectorPagination",
      discriminatorProperty = JsonMergePatch.NESTED_TYPE_PROPERTY,
      discriminatorMapping =
          @DiscriminatorMapping(value = PageNumber.TYPE, schema = PageNumber.class))
  public sealed interface Pagination permits PageNumber {
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
                  "Number of records per page. At least 1, and not larger than the maximum page"
                      + " size of the API: the API then sends fewer records than the page size,"
                      + " and the run stops after the first page without an error.",
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
}
