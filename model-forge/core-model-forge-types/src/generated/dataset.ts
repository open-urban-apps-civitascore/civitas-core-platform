// Generated from https://civitasconnect.digital/core-dataset/v1 — run `node generate.mjs` to regenerate
import { z } from "zod";
import { MappingSchema }    from "./mapping.js";
import { PipelineSchema }   from "./pipeline.js";
import { DataSourceSchema } from "./datasource.js";
import { DataSinkSchema }   from "./datasink.js";

// ── Root schema ────────────────────────────────────────────────────────────

/** A CORE DataSet: data structures (JSON Schema), declarative mappings, pipelines, data sources and data sinks. */
export const DataSetSchema = z.object({
  $schema: z.literal("https://civitasconnect.digital/core-dataset/v1"),
  /** Versioned CORE URN of this DataSet. */
  id: z.string().regex(/^urn:core:[^:]+:[^:]+:dataset:[^:]+:[^:]+:[^:]+$/, "Must be a versioned CORE DataSet URN"),
  title: z.string().optional(),
  description: z.string().optional(),
  version: z.string().optional(),
  /** URNs of globally persisted Elements. */
  elementRefs: z.array(z.string().regex(/^urn:/, "Must be a CORE URN")).optional(),
  /** URNs of globally persisted Mappings. */
  mappingRefs: z.array(z.string().regex(/^urn:/, "Must be a CORE URN")).optional(),
  /** URNs of globally persisted Pipelines. */
  pipelineRefs: z.array(z.string().regex(/^urn:/, "Must be a CORE URN")).optional(),
  /** URNs of globally persisted DataSources. */
  dataSourceRefs: z.array(z.string().regex(/^urn:/, "Must be a CORE URN")).optional(),
  /** URNs of globally persisted DataSinks. */
  dataSinkRefs: z.array(z.string().regex(/^urn:/, "Must be a CORE URN")).optional(),
  /** Named JSON Schema definitions. Present in resolved API responses; in storage replaced by elementRefs. */
  elements: z.record(z.string(), z.record(z.string(), z.unknown())).optional(),
  /** Declarative field mappings between elements. */
  mappings: z.array(MappingSchema).optional(),
  /** Integration pipelines as directed acyclic graphs. */
  pipelines: z.array(PipelineSchema).optional(),
  /** Named data sources. */
  dataSources: z.array(DataSourceSchema).optional(),
  /** Named data sinks. */
  dataSinks: z.array(DataSinkSchema).optional(),
});

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type DataSet = z.infer<typeof DataSetSchema>;
