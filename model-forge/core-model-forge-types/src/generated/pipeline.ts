// Generated from https://civitasconnect.digital/core/pipeline/v1 — run `node generate.mjs` to regenerate
import { z } from "zod";

// ── $defs ──────────────────────────────────────────────────────────────────

/** Visual position in the pipeline canvas (x/y coordinates in pixels). */
export const PositionSchema = z.object({
  x: z.number().optional(),
  y: z.number().optional(),
});

/** Common fields shared by all pipeline nodes. */
const PipelineNodeBaseSchema = z.object({
  id: z.string(),
  kind: z.string(),
  label: z.string().optional(),
  description: z.string().optional(),
  "x-ui-position": PositionSchema.optional(),
});

/** Entry point of the pipeline. Exactly one per pipeline. */
export const StartNodeSchema = PipelineNodeBaseSchema.extend({
  kind: z.literal("start"),
});

/** Exit point of the pipeline. Exactly one per pipeline. */
export const EndNodeSchema = PipelineNodeBaseSchema.extend({
  kind: z.literal("end"),
});

/** Reads structured data from a DataSource (e.g. MQTT broker). */
export const SourceNodeSchema = PipelineNodeBaseSchema.extend({
  kind: z.literal("source"),
  /**
   * CORE URN of the referenced DataSource.
   * @coreRef { type: "urn:core:type:DataSource" }
   */
  sourceRef: z.string(),
});

/** Filters events with a boolean expression. */
export const FilterNodeSchema = PipelineNodeBaseSchema.extend({
  kind: z.literal("filter"),
  expression: z.string(),
});

/** Enriches events through a lookup source. */
export const EnrichNodeSchema = PipelineNodeBaseSchema.extend({
  kind: z.literal("enrich"),
  /**
   * CORE URN of the DataSource used for enrichment lookups.
   * @coreRef { type: "urn:core:type:DataSource" }
   */
  lookupSourceRef: z.string(),
  lookupKey: z.string(),
});

/** Applies a declarative field mapping to transform the data stream. */
export const MappingNodeSchema = PipelineNodeBaseSchema.extend({
  kind: z.literal("mapping"),
  /**
   * CORE URN of the referenced Mapping.
   * @coreRef { type: "urn:core:type:Mapping" }
   */
  mappingRef: z.string(),
});

/** Writes transformed data to a DataSink (e.g. SQL database). */
export const SinkNodeSchema = PipelineNodeBaseSchema.extend({
  kind: z.literal("sink"),
  /**
   * CORE URN of the referenced DataSink.
   * @coreRef { type: "urn:core:type:DataSink" }
   */
  sinkRef: z.string(),
});

/** Splits one incoming flow record into multiple outgoing records. */
export const SplitNodeSchema = PipelineNodeBaseSchema.extend({
  kind: z.literal("split"),
});

/** Discriminated union of all pipeline node types. The 'kind' property selects the concrete type. */
export const PipelineNodeSchema = z.discriminatedUnion("kind", [
  StartNodeSchema,
  EndNodeSchema,
  SourceNodeSchema,
  FilterNodeSchema,
  EnrichNodeSchema,
  MappingNodeSchema,
  SinkNodeSchema,
  SplitNodeSchema
]);

/** A directed connection between two pipeline nodes. */
export const PipelineEdgeSchema = z.object({
  id: z.string(),
  /** ID of the source node. */
  source: z.string(),
  /** ID of the target node. */
  target: z.string(),
  label: z.string().optional(),
  /** 'data' edges carry the transformed payload; 'control' edges signal completion. */
  kind: z.enum(["data", "control"]).optional(),
});

// ── Root schema ────────────────────────────────────────────────────────────

/** A CORE integration pipeline — a directed acyclic graph (DAG) connecting data sources, mapping transformations, and data sinks. Each exported Pipeline document carries '$schema': 'https://civitasconnect.digital/core/pipeline/v1'. */
export const PipelineSchema = z.object({
  $schema: z.enum(["https://civitasconnect.digital/core/pipeline/v1", "https://civitasconnect.digital/core-dataset/v1#/$defs/Pipeline"]),
  /** Versioned CORE URN of this Pipeline. */
  id: z.string().regex(/^urn:core:[^:]+:[^:]+:pipeline:[^:]+:[^:]+:[^:]+$/, "Must be a versioned CORE Pipeline URN"),
  /** Human-readable display name. */
  title: z.string().optional(),
  description: z.string().optional(),
  /** When true, MiNiFi emits additional debug log events. */
  debug: z.boolean().optional(),
  /** Ordered list of pipeline nodes. */
  nodes: z.array(PipelineNodeSchema),
  /** Connections between nodes. */
  edges: z.array(PipelineEdgeSchema),
});

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type Position = z.infer<typeof PositionSchema>;
export type StartNode = z.infer<typeof StartNodeSchema>;
export type EndNode = z.infer<typeof EndNodeSchema>;
export type SourceNode = z.infer<typeof SourceNodeSchema>;
export type FilterNode = z.infer<typeof FilterNodeSchema>;
export type EnrichNode = z.infer<typeof EnrichNodeSchema>;
export type MappingNode = z.infer<typeof MappingNodeSchema>;
export type SinkNode = z.infer<typeof SinkNodeSchema>;
export type SplitNode = z.infer<typeof SplitNodeSchema>;
export type PipelineNode = z.infer<typeof PipelineNodeSchema>;
export type PipelineEdge = z.infer<typeof PipelineEdgeSchema>;
export type Pipeline = z.infer<typeof PipelineSchema>;
