// Generated from https://civitasconnect.digital/core/datasource/v1 — do not edit. Run `npm run generate:core-types` to regenerate.
import { z } from 'zod'

// ── $defs ──────────────────────────────────────────────────────────────────

/** Subscribes to one or more MQTT brokers and topics. */
export const MqttDataSourceSchema = z
  .object({
    $schema: z.enum([
      'https://civitasconnect.digital/core/datasource/v1',
      'https://civitasconnect.digital/core-dataset/v1#/$defs/DataSource',
    ]),
    /** Versioned CORE URN of this DataSource. */
    id: z
      .string()
      .regex(
        /^urn:core:[^:]+:[^:]+:datasource:[^:]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(:[^:]+)?$/,
        'Must be a versioned CORE DataSource URN',
      ),
    /** Human-readable display name. */
    title: z.string().optional(),
    description: z.string().optional(),
    /** MQTT broker subscription source. */
    connectionType: z.literal('mqtt'),
    /**
     * Versioned CORE URN of the DataStructure that describes the payload format.
     * @coreRef { type: "urn:core:type:DataStructure" }
     */
    element: z
      .string()
      .regex(
        /^urn:core:[^:]+:[^:]+:(datastructure|element):[^:]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(:[^:]+)?$/,
        'Must be a versioned CORE DataStructure URN',
      )
      .optional(),
    /** MQTT broker URLs, e.g. 'mqtt://mosquitto:1883'. */
    urls: z.union([z.array(z.string()), z.null()]).optional(),
    /** MQTT topic filter(s) to subscribe to. Wildcards (+, #) are supported. */
    topics: z.union([z.array(z.string()), z.null()]).optional(),
    /** MQTT Quality of Service level: 0, 1 or 2. */
    qos: z.union([z.literal(0), z.literal(1), z.literal(2), z.null()]).optional(),
    /** MQTT protocol version: '3' (auto-select 3.1 or 3.1.1) or '5' (5.0). */
    protocol_version: z.union([z.literal('3'), z.literal('5'), z.null()]).optional(),
    /** Connection timeout, e.g. '5s'. */
    connect_timeout: z.union([z.string(), z.null()]).optional(),
    /** Keep-alive interval, e.g. '30s'. */
    keepalive: z.union([z.string(), z.null()]).optional(),
    /** TLS settings for the broker connection. */
    tls: z
      .union([
        z
          .object({
            enabled: z.boolean().optional(),
          })
          .strict(),
        z.null(),
      ])
      .optional(),
    /** MQTT username. */
    user: z.union([z.string(), z.null()]).optional(),
    /** MQTT password. Stored ENCRYPTED by the host; null/absent when unset. */
    password: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

/** Polls a relational database via a JDBC/SQL query. */
export const SqlDataSourceSchema = z
  .object({
    $schema: z.enum([
      'https://civitasconnect.digital/core/datasource/v1',
      'https://civitasconnect.digital/core-dataset/v1#/$defs/DataSource',
    ]),
    /** Versioned CORE URN of this DataSource. */
    id: z
      .string()
      .regex(
        /^urn:core:[^:]+:[^:]+:datasource:[^:]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(:[^:]+)?$/,
        'Must be a versioned CORE DataSource URN',
      ),
    /** Human-readable display name. */
    title: z.string().optional(),
    description: z.string().optional(),
    /** SQL/JDBC polling source. */
    connectionType: z.literal('sql'),
    /**
     * Versioned CORE URN of the DataStructure that describes the row format.
     * @coreRef { type: "urn:core:type:DataStructure" }
     */
    element: z
      .string()
      .regex(
        /^urn:core:[^:]+:[^:]+:(datastructure|element):[^:]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(:[^:]+)?$/,
        'Must be a versioned CORE DataStructure URN',
      )
      .optional(),
    /** Database driver, e.g. 'postgres'. Only 'postgres' is wired in the NiFi engine. */
    driver: z.union([z.string(), z.null()]).optional(),
    /** Connection URL / data-source name (embedded credentials are stripped). */
    dsn: z.union([z.string(), z.null()]).optional(),
    /** Table to poll. */
    table: z.union([z.string(), z.null()]).optional(),
    /** Columns to select. */
    columns: z.union([z.array(z.string()), z.null()]).optional(),
    /** SQL WHERE clause. */
    where: z.union([z.string(), z.null()]).optional(),
    /** Database login username. */
    user: z.union([z.string(), z.null()]).optional(),
    /** Database login password. Stored ENCRYPTED by the host; null/absent when unset. */
    password: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

export const HttpNoAuthenticationSchema = z
  .object({
    type: z.literal('NONE'),
  })
  .strict()

export const HttpBasicAuthenticationSchema = z
  .object({
    type: z.literal('BASIC'),
    /** Basic auth username. */
    username: z.union([z.string(), z.null()]).optional(),
    /** Basic auth password. Stored ENCRYPTED by the host; null/absent when unset. */
    password: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

export const HttpBearerTokenAuthenticationSchema = z
  .object({
    type: z.literal('BEARER_TOKEN'),
    /** Bearer token, sent as 'Authorization: Bearer <token>'. Stored ENCRYPTED by the host; null/absent when unset. */
    token: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

export const HttpApiTokenAuthenticationSchema = z
  .object({
    type: z.literal('API_TOKEN'),
    /** Name of the header or query parameter that carries the token. */
    name: z.union([z.string(), z.null()]).optional(),
    /** API token. Stored ENCRYPTED by the host; null/absent when unset. */
    value: z.union([z.string(), z.null()]).optional(),
    /** Where each request sends the token. */
    in: z.union([z.literal('HEADER'), z.literal('QUERY'), z.null()]).optional(),
  })
  .strict()

export const HttpOAuth2AuthenticationSchema = z
  .object({
    type: z.literal('OAUTH2'),
    /** Token endpoint of the client credentials grant: an https URL without user info, query or fragment. */
    tokenUrl: z.union([z.string(), z.null()]).optional(),
    /** Client ID. */
    clientId: z.union([z.string(), z.null()]).optional(),
    /** Client secret. Stored ENCRYPTED by the host; null/absent when unset. */
    clientSecret: z.union([z.string(), z.null()]).optional(),
    /** Scope of the access token, space-separated; null for none. */
    scope: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

export const HttpClientCertificateAuthenticationSchema = z
  .object({
    type: z.literal('CLIENT_CERTIFICATE'),
    /** Client certificate in PEM, optionally followed by its chain. Stored ENCRYPTED by the host; null/absent when unset. */
    certificate: z.union([z.string(), z.null()]).optional(),
    /** Private key of the certificate in PEM, without a passphrase. Stored ENCRYPTED by the host; null/absent when unset. */
    privateKey: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

export const HttpParameterSchema = z
  .object({
    name: z.union([z.string(), z.null()]).optional(),
    value: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

export const HttpPageNumberPaginationSchema = z
  .object({
    type: z.literal('PAGE_NUMBER'),
    /** Query parameter that carries the page number. */
    pageParameter: z.union([z.string(), z.null()]).optional(),
    /** Query parameter that carries the page size. */
    pageSizeParameter: z.union([z.string(), z.null()]).optional(),
    /** Number of records per page. */
    pageSize: z.union([z.number().int(), z.null()]).optional(),
    /** First page number. */
    startPage: z.union([z.number().int(), z.null()]).optional(),
    /** Last page number; null for no end page. */
    endPage: z.union([z.number().int(), z.null()]).optional(),
  })
  .strict()

export const HttpLimitOffsetPaginationSchema = z
  .object({
    type: z.literal('LIMIT_OFFSET'),
    /** Query parameter that carries the limit. */
    limitParameter: z.union([z.string(), z.null()]).optional(),
    /** Query parameter that carries the offset. */
    offsetParameter: z.union([z.string(), z.null()]).optional(),
    /** Number of records per request. */
    limit: z.union([z.number().int(), z.null()]).optional(),
    /** Offset of the first request. */
    offset: z.union([z.number().int(), z.null()]).optional(),
    /** JSON path in the response to the number of records, or to an array whose length is that number; null counts the elements of the response, which is then an array. */
    countPath: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

export const HttpCursorPaginationSchema = z
  .object({
    type: z.literal('CURSOR'),
    /** JSON path in the response to the cursor of the next request. */
    cursorPath: z.union([z.string(), z.null()]).optional(),
    /** Name of the query parameter or header that carries the cursor. */
    cursorParameter: z.union([z.string(), z.null()]).optional(),
    /** Where each request sends the cursor. */
    cursorIn: z.union([z.literal('QUERY'), z.literal('HEADER'), z.null()]).optional(),
    /** Query parameter that carries the limit. */
    limitParameter: z.union([z.string(), z.null()]).optional(),
    /** Number of records per request. */
    limit: z.union([z.number().int(), z.null()]).optional(),
  })
  .strict()

export const HttpApiCallSchema = z
  .object({
    /** Name of the API call, unique within the DataSource. */
    name: z.union([z.string(), z.null()]).optional(),
    /** HTTP method. Only GET is supported. */
    method: z.literal('GET').optional(),
    /** Path relative to 'baseUrl', starting with '/'. */
    path: z.union([z.string(), z.null()]).optional(),
    /** Query parameters, in clear text. */
    queryParameters: z.union([z.array(HttpParameterSchema), z.null()]).optional(),
    /** Request headers, in clear text. */
    headers: z.union([z.array(HttpParameterSchema), z.null()]).optional(),
    /** Pagination of the API call, chosen by 'type'; null for none. */
    pagination: z
      .union([HttpPageNumberPaginationSchema, HttpLimitOffsetPaginationSchema, HttpCursorPaginationSchema, z.null()])
      .optional(),
  })
  .strict()

/** Reads from an HTTP/REST API with one or more named GET API calls. */
export const HttpDataSourceSchema = z
  .object({
    $schema: z.enum([
      'https://civitasconnect.digital/core/datasource/v1',
      'https://civitasconnect.digital/core-dataset/v1#/$defs/DataSource',
    ]),
    /** Versioned CORE URN of this DataSource. */
    id: z
      .string()
      .regex(
        /^urn:core:[^:]+:[^:]+:datasource:[^:]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(:[^:]+)?$/,
        'Must be a versioned CORE DataSource URN',
      ),
    /** Human-readable display name. */
    title: z.string().optional(),
    description: z.string().optional(),
    /** HTTP/REST API source. */
    connectionType: z.literal('http'),
    /**
     * Versioned CORE URN of the DataStructure that describes the payload format.
     * @coreRef { type: "urn:core:type:DataStructure" }
     */
    element: z
      .string()
      .regex(
        /^urn:core:[^:]+:[^:]+:(datastructure|element):[^:]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(:[^:]+)?$/,
        'Must be a versioned CORE DataStructure URN',
      )
      .optional(),
    /** API host: an http or https URL without user info, query, fragment or trailing '/'. */
    baseUrl: z.union([z.string(), z.null()]).optional(),
    /** Authentication of every API call, chosen by 'type'. */
    authentication: z
      .union([
        HttpNoAuthenticationSchema,
        HttpBasicAuthenticationSchema,
        HttpBearerTokenAuthenticationSchema,
        HttpApiTokenAuthenticationSchema,
        HttpOAuth2AuthenticationSchema,
        HttpClientCertificateAuthenticationSchema,
        z.null(),
      ])
      .optional(),
    /** The GET API calls. A source node names the one it reads by 'apiCallName'. */
    apiCalls: z.union([z.array(HttpApiCallSchema), z.null()]).optional(),
  })
  .strict()

// ── Root schema ────────────────────────────────────────────────────────────

/** Configuration for a data source that feeds data into a CORE pipeline. The connector-specific fields are chosen by 'connectionType' (mqtt | sql | http); each variant declares exactly the fields the host persists (the connector configuration POJO). Only the fields whose description says 'Stored ENCRYPTED' are stored ENCRYPTED by the host — every other field is stored as-is. 'element' references the DataStructure describing the payload format by versioned CORE URN. */
export const DataSourceSchema = z.discriminatedUnion('connectionType', [
  MqttDataSourceSchema,
  SqlDataSourceSchema,
  HttpDataSourceSchema,
])

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type MqttDataSource = z.infer<typeof MqttDataSourceSchema>
export type SqlDataSource = z.infer<typeof SqlDataSourceSchema>
export type HttpNoAuthentication = z.infer<typeof HttpNoAuthenticationSchema>
export type HttpBasicAuthentication = z.infer<typeof HttpBasicAuthenticationSchema>
export type HttpBearerTokenAuthentication = z.infer<typeof HttpBearerTokenAuthenticationSchema>
export type HttpApiTokenAuthentication = z.infer<typeof HttpApiTokenAuthenticationSchema>
export type HttpOAuth2Authentication = z.infer<typeof HttpOAuth2AuthenticationSchema>
export type HttpClientCertificateAuthentication = z.infer<typeof HttpClientCertificateAuthenticationSchema>
export type HttpParameter = z.infer<typeof HttpParameterSchema>
export type HttpPageNumberPagination = z.infer<typeof HttpPageNumberPaginationSchema>
export type HttpLimitOffsetPagination = z.infer<typeof HttpLimitOffsetPaginationSchema>
export type HttpCursorPagination = z.infer<typeof HttpCursorPaginationSchema>
export type HttpApiCall = z.infer<typeof HttpApiCallSchema>
export type HttpDataSource = z.infer<typeof HttpDataSourceSchema>
export type DataSource = z.infer<typeof DataSourceSchema>
