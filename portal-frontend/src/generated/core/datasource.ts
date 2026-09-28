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
      .regex(/^urn:core:[^:]+:[^:]+:datasource:[^:]+:[^:]+:[^:]+(:[^:]+)?$/, 'Must be a versioned CORE DataSource URN'),
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
        /^urn:core:[^:]+:[^:]+:(datastructure|element):[^:]+:[^:]+:[^:]+(:[^:]+)?$/,
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
      .regex(/^urn:core:[^:]+:[^:]+:datasource:[^:]+:[^:]+:[^:]+(:[^:]+)?$/, 'Must be a versioned CORE DataSource URN'),
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
        /^urn:core:[^:]+:[^:]+:(datastructure|element):[^:]+:[^:]+:[^:]+(:[^:]+)?$/,
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
    /** SQL prefix prepended to the query (not honored by the NiFi engine). */
    prefix: z.union([z.string(), z.null()]).optional(),
    /** SQL suffix appended to the query (not honored by the NiFi engine). */
    suffix: z.union([z.string(), z.null()]).optional(),
    /** SQL executed on connection init (not honored by the NiFi engine). */
    init_statement: z.union([z.string(), z.null()]).optional(),
    /** Maximum idle time per connection, e.g. '10m'. */
    conn_max_idle_time: z.union([z.string(), z.null()]).optional(),
    /** Maximum lifetime per connection, e.g. '1h'. */
    conn_max_life_time: z.union([z.string(), z.null()]).optional(),
    /** Maximum number of idle connections. Default 2. */
    conn_max_idle: z.number().int().optional(),
    /** Maximum number of open connections. 0 = unlimited. Default 0. */
    conn_max_open: z.number().int().optional(),
    /** Database login username. */
    user: z.union([z.string(), z.null()]).optional(),
    /** Database login password. Stored ENCRYPTED by the host; null/absent when unset. */
    password: z.union([z.string(), z.null()]).optional(),
  })
  .strict()

// ── Root schema ────────────────────────────────────────────────────────────

/** Configuration for a data source that feeds data into a CORE pipeline. The connector-specific fields are chosen by 'connectionType' (mqtt | sql); each variant declares exactly the fields the host persists (the connector configuration POJO). Only 'password' is stored ENCRYPTED by the host — every other field is stored as-is. 'element' references the DataStructure describing the payload format by versioned CORE URN. */
export const DataSourceSchema = z.discriminatedUnion('connectionType', [MqttDataSourceSchema, SqlDataSourceSchema])

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type MqttDataSource = z.infer<typeof MqttDataSourceSchema>
export type SqlDataSource = z.infer<typeof SqlDataSourceSchema>
export type DataSource = z.infer<typeof DataSourceSchema>
