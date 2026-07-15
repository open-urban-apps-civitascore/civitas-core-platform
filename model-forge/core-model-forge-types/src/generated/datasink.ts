// Generated from https://civitasconnect.digital/core/datasink/v1 — run `node generate.mjs` to regenerate
import { z } from "zod";

// ── Root schema ────────────────────────────────────────────────────────────

/** Configuration for a data sink that receives transformed data from a CORE pipeline. Currently supports SQL databases via JDBC. The 'element' field references an Element by versioned CORE URN — this constraint is enforced in the DataSet context. */
export const DataSinkSchema = z.object({
  $schema: z.enum(["https://civitasconnect.digital/core/datasink/v1", "https://civitasconnect.digital/core-dataset/v1#/$defs/DataSink"]),
  /** Versioned CORE URN of this DataSink. */
  id: z.string().regex(/^urn:core:[^:]+:[^:]+:datasink:[^:]+:[^:]+:[^:]+$/, "Must be a versioned CORE DataSink URN"),
  /** Human-readable display name. */
  title: z.string().optional(),
  description: z.string().optional(),
  /** Protocol used by this data sink. */
  connectionType: z.enum(["sql", "http"]),
  /**
   * Versioned CORE URN of the Element that describes the output row format.
   * @coreRef { type: "urn:core:type:Element" }
   */
  element: z.string().optional(),
  /** JDBC connection URL, e.g. 'jdbc:postgresql://postgres:5432/coredata'. */
  jdbcUrl: z.string().optional(),
  /** Target database table name. */
  tableName: z.string().optional(),
  /** Database schema (namespace). Defaults to 'public' for PostgreSQL. */
  dbSchema: z.string().optional(),
  /** Database login username. */
  username: z.string().optional(),
  /** Database login password. Do not commit plaintext credentials — use secrets management in production. */
  password: z.string().optional(),
  /** HTTP endpoint URL for HTTP sinks. */
  url: z.string().optional(),
  /** HTTP method for HTTP sinks. */
  method: z.enum(["POST", "PUT", "PATCH"]).optional(),
  /** Payload content type for HTTP sinks. */
  contentType: z.string().optional(),
});

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type DataSink = z.infer<typeof DataSinkSchema>;
