// Generated from https://civitasconnect.digital/core/datasource/v1 — run `node generate.mjs` to regenerate
import { z } from "zod";

// ── Root schema ────────────────────────────────────────────────────────────

/** Configuration for a data source that feeds structured data into a CORE pipeline. Currently supports MQTT brokers. The 'element' field references an Element by versioned CORE URN — this constraint is enforced in the DataSet context. */
export const DataSourceSchema = z.object({
  $schema: z.enum(["https://civitasconnect.digital/core/datasource/v1", "https://civitasconnect.digital/core-dataset/v1#/$defs/DataSource"]),
  /** Versioned CORE URN of this DataSource. */
  id: z.string().regex(/^urn:core:[^:]+:[^:]+:datasource:[^:]+:[^:]+:[^:]+$/, "Must be a versioned CORE DataSource URN"),
  /** Human-readable display name. */
  title: z.string().optional(),
  description: z.string().optional(),
  /** Protocol used by this data source. */
  connectionType: z.enum(["mqtt", "database", "http"]),
  /**
   * Versioned CORE URN of the Element that describes the payload format.
   * @coreRef { type: "urn:core:type:Element" }
   */
  element: z.string().optional(),
  /** MQTT broker URL, e.g. 'mqtt://mosquitto:1883'. */
  brokerUrl: z.string().optional(),
  /** MQTT topic to subscribe to. Wildcards (+, #) are supported. */
  topic: z.string().optional(),
  /** Optional MQTT client ID. Auto-generated if omitted. */
  clientId: z.string().optional(),
  /** MQTT Quality of Service level: 0 = at most once, 1 = at least once, 2 = exactly once. */
  qos: z.number().int().min(0).max(2).optional(),
  /** JDBC connection URL for database-backed lookup sources. */
  jdbcUrl: z.string().optional(),
  /** Database login username. */
  username: z.string().optional(),
  /** Database login password. Do not commit plaintext credentials. */
  password: z.string().optional(),
  /** Database table used for enrichment lookups. */
  lookupTable: z.string().optional(),
  /** Database key column used for enrichment lookups. */
  lookupKey: z.string().optional(),
  /** HTTP endpoint URL for HTTP sources. */
  url: z.string().optional(),
  /** HTTP method for HTTP sources. */
  method: z.enum(["GET", "POST"]).optional(),
});

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type DataSource = z.infer<typeof DataSourceSchema>;
