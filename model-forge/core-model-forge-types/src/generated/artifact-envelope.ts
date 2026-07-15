// Generated from https://civitasconnect.digital/core/artifact-envelope/v1 — run `node generate.mjs` to regenerate
import { z } from "zod";

// ── $defs ──────────────────────────────────────────────────────────────────

/** Artifact reference using the registry exchange field names (groupId/artifactId/version/name) — this is the persistence/exchange boundary, not the CORE domain vocabulary. */
export const ArtifactReferenceSchema = z.object({
  groupId: z.string(),
  artifactId: z.string(),
  version: z.string(),
  name: z.string(),
}).strict();

export const ArtifactContentSchema = z.object({
  contentType: z.enum(["application/schema+json", "application/json", "application/xml", "text/xml"]),
  /** Artifact payload. JSON Schema content is normally an object; XSD content is an XML string. */
  content: z.union([z.record(z.string(), z.unknown()), z.string()]),
  references: z.array(ArtifactReferenceSchema).optional(),
}).strict();

export const ArtifactVersionSchema = z.object({
  version: z.string(),
  title: z.string().optional(),
  description: z.string().optional(),
  content: ArtifactContentSchema,
}).strict();

// ── Root schema ────────────────────────────────────────────────────────────

/** Envelope for importing or exchanging CORE artifacts with metadata and one content version. */
export const ArtifactEnvelopeSchema = z.object({
  $schema: z.literal("https://civitasconnect.digital/core/artifact-envelope/v1"),
  /** Logical artifact id — the version-free CORE URN; combine it with firstVersion.version for the versioned artifact URN. */
  artifactId: z.string().regex(/^urn:core:/, "Must be a CORE URN"),
  /** Artifact content type as used by the global artifact store. */
  artifactType: z.enum(["JSON", "JSON_SCHEMA", "XSD", "MAPPING", "PIPELINE", "DATASOURCE", "DATASINK", "DATASET"]),
  /** Global artifact group. */
  groupId: z.enum(["elements", "mappings", "pipelines", "datasources", "datasinks", "datasets"]).optional(),
  /** Human-readable display name. */
  title: z.string().optional(),
  description: z.string().optional(),
  labels: z.record(z.string(), z.string()).optional(),
  properties: z.record(z.string(), z.string()).optional(),
  firstVersion: ArtifactVersionSchema,
}).strict();

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type ArtifactReference = z.infer<typeof ArtifactReferenceSchema>;
export type ArtifactContent = z.infer<typeof ArtifactContentSchema>;
export type ArtifactVersion = z.infer<typeof ArtifactVersionSchema>;
export type ArtifactEnvelope = z.infer<typeof ArtifactEnvelopeSchema>;
